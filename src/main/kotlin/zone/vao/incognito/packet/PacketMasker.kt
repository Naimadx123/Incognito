package zone.vao.incognito.packet

import io.netty.channel.Channel
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import zone.vao.incognito.Incognito
import zone.vao.incognito.config.IncognitoConfig
import zone.vao.incognito.identity.IncognitoService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent
import zone.vao.incognito.coordinate.CoordinateOffset

class PacketMasker(
    private val plugin: Incognito,
    private val service: IncognitoService,
    private val settings: IncognitoConfig,
) : Listener, AutoCloseable {

    private val native = NativePackets(settings, plugin.logger, service::maskHead, service::suggestionIdentities)
    private val channels = ConcurrentHashMap<UUID, Channel>()
    private val sessionOffsets = ConcurrentHashMap<UUID, CoordinateOffset>()
    private val failures = ConcurrentHashMap.newKeySet<String>()
    private val handlerName = "incognito_mask"
    @Volatile private var closed = false

    fun register() {
        native.validate()
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.server.onlinePlayers.forEach { player ->
            if (service.offset(player.uniqueId) != CoordinateOffset.ZERO) {
                service.region(player) { player.kick(settings.messages.get("reconnect-required")) }
            } else inject(player)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onConfigure(event: PlayerConnectionInitialConfigureEvent) {
        val connection = event.connection
        try {
            val id = requireNotNull(connection.profile.id)
            install(id, channel(connection), service.offset(id))
        } catch (exception: Exception) {
            plugin.logger.severe("Cannot prepare the Incognito connection: ${exception.javaClass.simpleName}")
            connection.disconnect(settings.messages.get("unsupported"))
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        inject(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        channels.remove(event.player.uniqueId)?.let(::remove)
        sessionOffsets.remove(event.player.uniqueId)
    }

    private fun inject(player: Player) {
        if (channels.containsKey(player.uniqueId)) return
        try {
            val handle = player.javaClass.getMethod("getHandle").invoke(player)
            val listener = NativeReflection.fields(handle.javaClass).first { it.type.simpleName == "ServerGamePacketListenerImpl" }.get(handle)
            check(service.offset(player.uniqueId) == CoordinateOffset.ZERO) { "Filter was not installed during configuration" }
            install(player.uniqueId, channel(listener), CoordinateOffset.ZERO)
        } catch (exception: Exception) {
            plugin.logger.severe("Cannot find the player channel: ${exception.javaClass.simpleName}")
            service.region(player) { player.kick(settings.messages.get("unsupported")) }
        }
    }

    private fun channel(listener: Any): Channel {
        val connection = NativeReflection.fields(listener.javaClass).first { it.type.name == "net.minecraft.network.Connection" }.get(listener)
        return NativeReflection.fields(connection.javaClass).first { Channel::class.java.isAssignableFrom(it.type) }.get(connection) as Channel
    }

    private fun install(id: UUID, channel: Channel, offset: CoordinateOffset) {
            channels[id] = channel
            sessionOffsets[id] = offset
            channel.closeFuture().addListener {
                if (channels.remove(id, channel)) sessionOffsets.remove(id)
            }
            val requests = ConcurrentHashMap<Int, SuggestionRequest>()
            val install = Runnable {
                if (!closed && channel.isActive) {
                    try {
                        check(channel.pipeline().get("packet_handler") != null) { "Missing packet_handler" }
                        if (settings.debug) plugin.logger.info("[debug] pipeline $id offset=$offset reveal=${plugin.server.getPlayer(id)?.hasPermission("incognito.reveal")}: ${channel.pipeline().names()}")
                        if (channel.pipeline().get(handlerName) == null) {
                            channel.pipeline().addBefore("packet_handler", handlerName, OutboundMaskHandler(
                                transform = { native.mask(it, service.identities(), offset, requests, id, plugin.server.getPlayer(id)?.hasPermission("incognito.reveal") == true) },
                                failure = { packet, error ->
                                    if (failures.add(packet.javaClass.name)) {
                                        plugin.logger.log(Level.SEVERE, "Blocked ${packet.javaClass.simpleName}: packet transformation failed.", error)
                                    }
                                    if (offset != CoordinateOffset.ZERO) channel.close()
                                },
                                inbound = { native.inbound(it, service.identities(), offset, requests, id) },
                            ))
                        }
                    } catch (exception: Exception) {
                        plugin.logger.severe("Cannot install the packet filter: ${exception.javaClass.simpleName}")
                        channel.close()
                    }
                }
            }
            if (channel.eventLoop().inEventLoop()) install.run() else channel.eventLoop().execute(install)
    }

    override fun close() {
        closed = true
        HandlerList.unregisterAll(this)
        channels.forEach { (id, channel) ->
            if (sessionOffsets[id] != CoordinateOffset.ZERO) channel.close() else remove(channel)
        }
        channels.clear()
        sessionOffsets.clear()
        native.close()
    }

    private fun remove(channel: Channel) {
        if (channel.eventLoop().isShuttingDown) return
        channel.eventLoop().execute {
            if (channel.pipeline().get(handlerName) != null) channel.pipeline().remove(handlerName)
        }
    }
}
