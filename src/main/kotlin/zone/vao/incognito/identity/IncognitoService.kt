package zone.vao.incognito.identity

import com.destroystokyo.paper.profile.ProfileProperty
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.entity.Player
import zone.vao.incognito.Incognito
import zone.vao.incognito.config.IncognitoConfig
import zone.vao.incognito.config.JoinQuitConfig
import zone.vao.incognito.command.CommandNames
import zone.vao.incognito.packet.NativeReflection
import zone.vao.incognito.packet.ComponentMasker
import zone.vao.incognito.packet.TextMasker
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.coordinate.CoordinateSessions
import zone.vao.incognito.network.NetworkClaim
import zone.vao.incognito.network.NetworkSession
import zone.vao.incognito.network.RedisNetwork
import zone.vao.incognito.hook.MapHooks
import java.util.concurrent.TimeUnit
import zone.vao.incognito.storage.PlayerDataService
import zone.vao.incognito.storage.PlayerRecord
import io.papermc.paper.threadedregions.scheduler.ScheduledTask

class IncognitoService(
    private val plugin: Incognito,
    val settings: IncognitoConfig,
    private val data: PlayerDataService,
    private val network: RedisNetwork? = null,
    private val maps: MapHooks? = null,
) {

    private val identities = ConcurrentHashMap<UUID, Identity>()
    private val sessionIdentities = IdentitySessions()
    private val preparedSessions = ConcurrentHashMap<UUID, Identity>()
    private val aliases = SessionAliases(settings.aliasFormat)
    private val headText = ComponentMasker(TextMasker(emptyList()))
    private val suggestionNames = SuggestionNames()
    private val nameTasks = ConcurrentHashMap<UUID, ScheduledTask>()
    private val adminMessages = AdminMessages()
    val coordinateSessions = CoordinateSessions()
    private val claims = ConcurrentHashMap<UUID, NetworkClaim>()
    private val continued = ConcurrentHashMap.newKeySet<UUID>()
    private val heartbeat = network?.let {
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            runCatching { it.refresh(identities().map(Identity::id)) }
                .onFailure { error -> plugin.logger.warning("Cannot refresh incognito sessions in Redis (${error.javaClass.simpleName}).") }
        }, 5, 10, TimeUnit.SECONDS)
    }
    private val playersByName: MutableMap<String, Any> by lazy {
        val server = plugin.server.javaClass.getMethod("getServer").invoke(plugin.server)
        val list = server.javaClass.getMethod("getPlayerList").invoke(server)
        @Suppress("UNCHECKED_CAST")
        NativeReflection.fields(list.javaClass).first { it.name == "playersByName" }.get(list) as MutableMap<String, Any>
    }

    fun identity(id: UUID): Identity? = identities[id] ?: preparedSessions[id]

    fun pending(id: UUID): Boolean? =
        (data.get(id)?.enabled == true).takeIf { it != (identity(id) != null) }

    fun status(id: UUID): Component {
        val pending = pending(id)
        if (pending != null) return settings.messages.get(if (pending) "pending-enabled" else "pending-disabled")
        return identity(id)?.let { settings.messages.get("status-enabled", it.alias) }
            ?: settings.messages.get("status-disabled")
    }

    fun identities(): List<Identity> = sessionIdentities.active()

    fun packetIdentities(): List<Identity> = sessionIdentities.packets()

    fun suggestionIdentities(): List<Identity> = suggestionNames.identities(identities())

    fun publicMessage(component: Component): Component =
        if (settings.names) headText.mask(component, packetIdentities(), CoordinateOffset.ZERO, resolvePlaceholders = false)
        else component

    fun systemMessage(component: Component): Component =
        if (settings.maskSystemMessages) publicMessage(component) else component

    fun rewriteCommand(command: String): String =
        if (settings.names && settings.hideRealName) CommandNames.rewrite(command, identities.values) { plugin.server.getPlayer(it)?.isOnline == true } else command

    fun offset(id: UUID): CoordinateOffset = if (settings.coordinates) coordinateSessions.get(id) else CoordinateOffset.ZERO

    fun claimSession(id: UUID) {
        val network = network ?: return
        try {
            if (data.get(id)?.enabled != true) {
                network.release(id)
                return
            }
            claims[id] = network.claim(id) { taken ->
                val alias = synchronized(aliases) { aliases.next(sessionIdentities.previous(id)?.alias) { available(it) && !taken(it) } }
                NetworkSession(alias, UUID.randomUUID(), CoordinateOffset.random())
            }
        } catch (error: Exception) {
            plugin.logger.warning("Cannot share the incognito session of $id through Redis (${error.javaClass.simpleName}); using a local session.")
        }
    }

    fun prepareSession(id: UUID, realName: String) {
        val claim = claims.remove(id)
        if (data.get(id)?.enabled != true) {
            sessionIdentities.remove(id)
            preparedSessions.remove(id)
            coordinateSessions.disable(id)
            continued.remove(id)
            return
        }
        if (settings.coordinates) coordinateSessions.enable(id, claim?.session?.offset)
        synchronized(aliases) {
            val identity = claim?.session?.let { Identity(id, realName, if (settings.names) it.alias else realName, it.maskedId) }
                ?: createIdentity(id, realName)
            if (claim?.continued == true) continued.add(id) else continued.remove(id)
            sessionIdentities.put(identity)
            preparedSessions[id] = identity
        }
    }

    fun forgetPreparedSession(id: UUID, identity: Identity) {
        if (preparedSessions.remove(id, identity)) sessionIdentities.retire(id, identity)
    }

    private fun createIdentity(id: UUID, realName: String): Identity = Identity(
        id, realName, if (settings.names) aliases.next(sessionIdentities.previous(id)?.alias, ::available) else realName,
    )

    fun region(player: Player, action: () -> Unit) {
        if (plugin.server.isOwnedByCurrentRegion(player)) action() else player.scheduler.run(plugin, { action() }, null)
    }

    fun load(player: Player) {
        region(player) {
            val record = data.get(player.uniqueId) ?: return@region
            if (!record.enabled) {
                record.lastAlias?.let { alias ->
                    data.save(record.copy(realName = player.name, lastAlias = null))
                    notifyAdmins("notify-disabled", alias, player.name)
                }
                return@region
            }
            if (identities.containsKey(player.uniqueId)) return@region
            val identity = preparedSessions.remove(player.uniqueId) ?: run {
                player.kick(settings.messages.get("reconnect-required"))
                return@region
            }
            identities[player.uniqueId] = identity
            maps?.hide(player)
            if (settings.names) {
                trackNames(player)
                rename(player, if (settings.hideRealName) player.name else identity.alias, identity.alias)
            }
            player.updateCommands()
            data.save(record.copy(realName = player.name, lastAlias = identity.alias))
            if (!continued.remove(player.uniqueId)) {
                notifyAdmins(if (record.lastAlias == null) "notify-enabled" else "notify-alias-changed", identity.alias, identity.realName)
            }
        }
    }

    fun change(player: Player, enabled: Boolean?, complete: (Boolean) -> Unit = {}) {
        region(player) {
            if (!player.isOnline) return@region
            val record = data.get(player.uniqueId) ?: PlayerRecord(player.uniqueId, player.name, false)
            val current = record.enabled
            val next = enabled ?: !current
            if (current != next) {
                data.save(record.copy(realName = player.name, enabled = next))
                val key = if (pending(player.uniqueId) == null) "notify-cancelled"
                    else if (next) "notify-pending-enabled" else "notify-pending-disabled"
                notifyAdmins(key, identity(player.uniqueId)?.alias.orEmpty(), player.name)
            }
            player.sendMessage(status(player.uniqueId))
            complete(next)
        }
    }

    fun joinQuitStatus(id: UUID): Component {
        val shown = settings.joinQuit.shows(data.get(id)?.showJoinQuit)
        val status = settings.messages.get(if (shown) "join-quit-shown" else "join-quit-hidden")
        return if (settings.joinQuit.canToggle) status
        else status.append(Component.space()).append(settings.messages.get("join-quit-locked"))
    }

    fun changeJoinQuit(player: Player, shown: Boolean?) {
        region(player) {
            if (!player.isOnline) return@region
            if (!settings.joinQuit.canToggle) {
                player.sendMessage(joinQuitStatus(player.uniqueId))
                return@region
            }
            val record = data.get(player.uniqueId) ?: PlayerRecord(player.uniqueId, player.name, false)
            data.save(record.copy(realName = player.name, showJoinQuit = shown ?: !settings.joinQuit.shows(record.showJoinQuit)))
            player.sendMessage(joinQuitStatus(player.uniqueId))
        }
    }

    fun joinQuitMessage(player: Player, original: Component?, joining: Boolean): Component? {
        val identity = identity(player.uniqueId) ?: return original?.let(::systemMessage)
        if (!settings.joinQuit.shows(data.get(player.uniqueId)?.showJoinQuit)) return null
        val message = original ?: if (settings.joinQuit.mode == JoinQuitConfig.Mode.SHOW) {
            val name = if (settings.maskSystemMessages) identity.alias else identity.realName
            settings.messages.get(if (joining) "join-message" else "quit-message", name, identity.realName)
        } else null
        return message?.let(::systemMessage)
    }

    private fun notifyAdmins(key: String, alias: String, realName: String) {
        val message = settings.messages.get(key, alias, realName)
        if (message == Component.empty()) return
        plugin.server.onlinePlayers.forEach { recipient ->
            region(recipient) {
                if (recipient.isOnline && recipient.hasPermission("incognito.admin")) {
                    recipient.sendMessage(adminMessages.prepare(recipient.uniqueId, message))
                }
            }
        }
    }

    internal fun resolveAdminMessage(component: Component, recipient: UUID?): Component? =
        adminMessages.resolve(component, recipient, recipient?.let { plugin.server.getPlayer(it)?.hasPermission("incognito.admin") } == true)

    fun maskHead(item: ItemStack): Boolean {
        if (item.type != Material.PLAYER_HEAD) return false
        val meta = item.itemMeta as? SkullMeta ?: return false
        val profile = meta.playerProfile ?: return false
        val identity = HeadProfiles.find(profile.id, profile.name, profile.properties.filter { it.name == "textures" }.map { it.value }, packetIdentities())
            ?: return false
        val masked = plugin.server.createProfileExact(if (settings.names || settings.skin) HeadProfiles.maskedId(identity) else identity.id, identity.alias)
        if (!settings.skin) profile.properties.forEach(masked::setProperty)
        else if (settings.texture.isNotEmpty()) masked.setProperty(ProfileProperty("textures", settings.texture, settings.signature))
        meta.playerProfile = masked
        if (settings.names) {
            val names = listOf(identity)
            meta.displayName()?.let { meta.displayName(headText.mask(it, names, CoordinateOffset.ZERO)) }
            if (meta.hasItemName()) meta.itemName(headText.mask(meta.itemName(), names, CoordinateOffset.ZERO))
            meta.lore()?.let { lore -> meta.lore(lore.map { headText.mask(it, names, CoordinateOffset.ZERO) }) }
        }
        item.itemMeta = meta
        return true
    }

    fun forget(player: Player) {
        preparedSessions.remove(player.uniqueId)
        continued.remove(player.uniqueId)
        network?.forget(player.uniqueId)
        restore(player, false)
        sessionIdentities.retire(player.uniqueId)
    }

    fun close() {
        heartbeat?.cancel()
        nameTasks.values.forEach { it.cancel() }
        nameTasks.clear()
        plugin.server.onlinePlayers.forEach { region(it) { restore(it) } }
    }

    private fun restore(player: Player, refresh: Boolean = true) {
        nameTasks.remove(player.uniqueId)?.cancel()
        suggestionNames.remove(player.uniqueId)
        val identity = identities.remove(player.uniqueId) ?: return
        maps?.show(player)
        if (settings.names) rename(player, identity.alias, if (refresh) player.name else null)
        if (refresh && (settings.names || settings.skin)) refresh(player)
        if (refresh) player.updateCommands()
    }

    private fun trackNames(player: Player) {
        fun update() {
            suggestionNames.update(player.uniqueId, player.displayName(), player.playerListName())
        }
        update()
        nameTasks.remove(player.uniqueId)?.cancel()
        player.scheduler.runAtFixedRate(plugin, { update() }, {
            nameTasks.remove(player.uniqueId)
            suggestionNames.remove(player.uniqueId)
        }, 1, 20)?.let { nameTasks[player.uniqueId] = it }
    }

    private fun available(alias: String): Boolean =
        plugin.server.onlinePlayers.none { it.name.equals(alias, true) } &&
            sessionIdentities.active().none { it.alias.equals(alias, true) || it.realName.equals(alias, true) }

    private fun rename(player: Player, from: String, to: String?) {
        val handle = player.javaClass.getMethod("getHandle").invoke(player)
        if (playersByName[from.lowercase()] === handle) playersByName.remove(from.lowercase())
        if (to != null) playersByName[to.lowercase()] = handle
    }

    private fun refresh(player: Player) {
        player.playerProfile = player.playerProfile
    }
}
