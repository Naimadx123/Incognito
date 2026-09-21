package zone.vao.incognito.identity

import com.destroystokyo.paper.profile.ProfileProperty
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.entity.Player
import zone.vao.incognito.Incognito
import zone.vao.incognito.config.IncognitoConfig
import zone.vao.incognito.packet.NativeReflection
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
    private val originalNames = ConcurrentHashMap<UUID, Pair<Component, Component?>>()
    private val suggestionNames = SuggestionNames()
    private val nameTasks = ConcurrentHashMap<UUID, ScheduledTask>()
    val coordinateSessions = CoordinateSessions(File(plugin.dataFolder, "coordinate-sessions.yml"))
    private val randomLength = minOf(10, 16 - settings.aliasFormat.replace("{random}", "").length)
    private val aliasPattern = Regex(settings.aliasFormat.split("{random}").joinToString("[a-f0-9]{$randomLength}") { Regex.escape(it) })
    private val playersByName: MutableMap<String, Any> by lazy {
        val server = plugin.server.javaClass.getMethod("getServer").invoke(plugin.server)
        val list = server.javaClass.getMethod("getPlayerList").invoke(server)
        @Suppress("UNCHECKED_CAST")
        NativeReflection.fields(list.javaClass).first { it.name == "playersByName" }.get(list) as MutableMap<String, Any>
    }

    fun identity(id: UUID): Identity? = identities[id] ?: data.get(id)?.takeIf { it.enabled }?.let {
        Identity(it.id, it.realName, if (settings.names) it.alias else it.realName)
    }

    fun identities(): List<Identity> = data.enabled().mapNotNull { identity(it.id) }

    fun suggestionIdentities(): List<Identity> = suggestionNames.identities(identities())

    fun offset(id: UUID): CoordinateOffset = if (settings.coordinates) coordinateSessions.get(id) else CoordinateOffset.ZERO

    fun region(player: Player, action: () -> Unit) {
        if (plugin.server.isOwnedByCurrentRegion(player)) action() else player.scheduler.run(plugin, { action() }, null)
    }

    fun load(player: Player) {
        region(player) {
            data.get(player.uniqueId)?.takeIf { it.enabled }?.let { enable(player, it.alias) }
        }
    }

    fun enable(player: Player, requested: String? = null, kick: Boolean = true): Identity {
        identities[player.uniqueId]?.let { return it }
        val alias = requested?.takeIf { it.matches(aliasPattern) && available(it, player.uniqueId) }
            ?: generateSequence { settings.aliasFormat.replace("{random}", UUID.randomUUID().toString().replace("-", "").take(randomLength)) }.first { available(it, player.uniqueId) }
        val reconnect = settings.coordinates && coordinateSessions.enable(player.uniqueId)
        val identity = Identity(player.uniqueId, player.name, if (settings.names) alias else player.name)
        identities[player.uniqueId] = identity
        data.save(PlayerRecord(player.uniqueId, identity.realName, alias, true))
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
            val record = data.get(player.uniqueId)
            data.save(PlayerRecord(player.uniqueId, player.name, record?.alias ?: player.name, false))
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
        val identity = profile.id?.let(::identity)
            ?: profile.name?.let { name -> identities.values.firstOrNull { it.realName.equals(name, true) } }
            ?: return false
        val masked = plugin.server.createProfile(identity.id, identity.alias)
        if (!settings.skin) profile.properties.forEach(masked::setProperty)
        else if (settings.texture.isNotEmpty()) masked.setProperty(ProfileProperty("textures", settings.texture, settings.signature))
        meta.playerProfile = masked
        item.itemMeta = meta
        return true
    }

    fun forget(player: Player) {
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

    private fun available(alias: String, id: UUID): Boolean =
        plugin.server.onlinePlayers.none { it.name.equals(alias, true) } &&
            data.enabled().none { it.id != id && it.alias.equals(alias, true) }

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
