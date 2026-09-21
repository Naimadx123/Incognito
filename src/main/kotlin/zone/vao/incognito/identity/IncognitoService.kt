package zone.vao.incognito.identity

import com.destroystokyo.paper.profile.ProfileProperty
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.entity.Player
import zone.vao.incognito.Incognito
import zone.vao.incognito.config.IncognitoConfig
import zone.vao.incognito.command.CommandNames
import zone.vao.incognito.packet.NativeReflection
import zone.vao.incognito.packet.ComponentMasker
import zone.vao.incognito.packet.TextMasker
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.coordinate.CoordinateSessions
import java.io.File
import zone.vao.incognito.storage.PlayerDataService
import zone.vao.incognito.storage.PlayerRecord
import io.papermc.paper.threadedregions.scheduler.ScheduledTask

class IncognitoService(private val plugin: Incognito, val settings: IncognitoConfig, private val data: PlayerDataService) {

    private val identities = ConcurrentHashMap<UUID, Identity>()
    private val sessionIdentities = ConcurrentHashMap<UUID, Identity>()
    private val preparedSessions = ConcurrentHashMap.newKeySet<UUID>()
    private val aliases = SessionAliases(settings.aliasFormat)
    private val headText = ComponentMasker(TextMasker(emptyList()))
    private val originalNames = ConcurrentHashMap<UUID, Pair<Component, Component?>>()
    private val suggestionNames = SuggestionNames()
    private val nameTasks = ConcurrentHashMap<UUID, ScheduledTask>()
    val coordinateSessions = CoordinateSessions(File(plugin.dataFolder, "coordinate-sessions.yml"))
    private val playersByName: MutableMap<String, Any> by lazy {
        val server = plugin.server.javaClass.getMethod("getServer").invoke(plugin.server)
        val list = server.javaClass.getMethod("getPlayerList").invoke(server)
        @Suppress("UNCHECKED_CAST")
        NativeReflection.fields(list.javaClass).first { it.name == "playersByName" }.get(list) as MutableMap<String, Any>
    }

    fun identity(id: UUID): Identity? = identities[id] ?: sessionIdentities[id]?.takeIf { id in preparedSessions }

    fun identities(): List<Identity> = sessionIdentities.values.toList()

    fun suggestionIdentities(): List<Identity> = suggestionNames.identities(identities())

    fun rewriteCommand(command: String): String =
        if (settings.names && settings.hideRealName) CommandNames.rewrite(command, identities.values) { plugin.server.getPlayer(it)?.isOnline == true } else command

    fun offset(id: UUID): CoordinateOffset = if (settings.coordinates) coordinateSessions.get(id) else CoordinateOffset.ZERO

    fun prepareSession(id: UUID, realName: String) {
        if (data.get(id)?.enabled != true) return
        sessionIdentities[id] = createIdentity(id, realName)
        preparedSessions.add(id)
    }

    private fun createIdentity(id: UUID, realName: String): Identity = Identity(
        id, realName, if (settings.names) aliases.next(sessionIdentities[id]?.alias, ::available) else realName,
    )

    fun region(player: Player, action: () -> Unit) {
        if (plugin.server.isOwnedByCurrentRegion(player)) action() else player.scheduler.run(plugin, { action() }, null)
    }

    fun load(player: Player) {
        region(player) {
            data.get(player.uniqueId)?.takeIf { it.enabled }?.let { enable(player) }
        }
    }

    fun enable(player: Player, kick: Boolean = true): Identity {
        identities[player.uniqueId]?.let { return it }
        val identity = if (preparedSessions.remove(player.uniqueId)) sessionIdentities.getValue(player.uniqueId) else createIdentity(player.uniqueId, player.name)
        val alias = identity.alias
        val reconnect = settings.coordinates && coordinateSessions.enable(player.uniqueId)
        identities[player.uniqueId] = identity
        sessionIdentities[player.uniqueId] = identity
        data.save(PlayerRecord(player.uniqueId, identity.realName, true))
        region(player) {
            if (settings.names) {
                originalNames[player.uniqueId] = player.displayName() to player.playerListName()
                trackNames(player)
                player.displayName(Component.text(alias))
                player.playerListName(Component.text(alias))
                rename(player, if (settings.hideRealName) player.name else alias, alias)
            }
            if (settings.names || settings.skin) refresh(player)
            if (settings.names) completions()
            player.updateCommands()
            if (reconnect && kick) {
                player.saveData()
                player.kick(settings.messages.get("reconnect-enabled"))
            } else if (reconnect) player.sendMessage(settings.messages.get("relog-enabled"))
        }
        return identity
    }

    fun disable(player: Player, kick: Boolean = true) {
        val reconnect = coordinateSessions.disable(player.uniqueId)
        region(player) {
            data.save(PlayerRecord(player.uniqueId, player.name, false))
            sessionIdentities.remove(player.uniqueId)
            restore(player)
            if (reconnect && kick) {
                player.saveData()
                player.kick(settings.messages.get("reconnect-disabled"))
            } else if (reconnect) player.sendMessage(settings.messages.get("relog-disabled"))
        }
    }

    fun maskHead(item: ItemStack): Boolean {
        if (item.type != Material.PLAYER_HEAD) return false
        val meta = item.itemMeta as? SkullMeta ?: return false
        val profile = meta.playerProfile ?: return false
        val identity = HeadProfiles.find(profile.id, profile.name, profile.properties.filter { it.name == "textures" }.map { it.value }, sessionIdentities.values)
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
        restore(player, false)
    }

    fun close() {
        nameTasks.values.forEach { it.cancel() }
        nameTasks.clear()
        plugin.server.onlinePlayers.forEach { region(it) { restore(it) } }
    }

    private fun restore(player: Player, refresh: Boolean = true) {
        nameTasks.remove(player.uniqueId)?.cancel()
        suggestionNames.remove(player.uniqueId)
        val identity = identities.remove(player.uniqueId) ?: return
        if (settings.names) rename(player, identity.alias, if (refresh) player.name else null)
        originalNames.remove(player.uniqueId)?.let { (display, list) ->
            player.displayName(display)
            player.playerListName(list)
        }
        if (refresh && (settings.names || settings.skin)) refresh(player)
        if (refresh && settings.names) completions()
        if (refresh) player.updateCommands()
    }

    private fun trackNames(player: Player) {
        fun update() {
            val original = originalNames[player.uniqueId]
            suggestionNames.update(player.uniqueId, original?.first, original?.second, player.displayName(), player.playerListName())
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
            identities.values.none { it.alias.equals(alias, true) }

    private fun rename(player: Player, from: String, to: String?) {
        val handle = player.javaClass.getMethod("getHandle").invoke(player)
        if (playersByName[from.lowercase()] === handle) playersByName.remove(from.lowercase())
        if (to != null) playersByName[to.lowercase()] = handle
    }

    private fun completions() {
        val names = plugin.server.onlinePlayers.map { it.name }
        plugin.server.onlinePlayers.forEach { viewer -> region(viewer) { viewer.setCustomChatCompletions(names) } }
    }

    private fun refresh(player: Player) {
        player.playerProfile = player.playerProfile
    }
}
