package zone.vao.incognito.identity

import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import zone.vao.incognito.Incognito
import zone.vao.incognito.config.IncognitoConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.coordinate.CoordinateSessions
import java.io.File

class IncognitoService(private val plugin: Incognito, val settings: IncognitoConfig) {

    private val key = NamespacedKey(plugin, "alias")
    private val identities = ConcurrentHashMap<UUID, Identity>()
    private val originalNames = ConcurrentHashMap<UUID, Pair<Component, Component?>>()
    val coordinateSessions = CoordinateSessions(File(plugin.dataFolder, "coordinate-sessions.yml"))

    fun identity(id: UUID): Identity? = identities[id]

    fun identities(): List<Identity> = identities.values.toList()

    fun offset(id: UUID): CoordinateOffset = if (settings.coordinates) coordinateSessions.get(id) else CoordinateOffset.ZERO

    fun region(player: Player, action: () -> Unit) {
        if (plugin.server.isOwnedByCurrentRegion(player)) action() else player.scheduler.run(plugin, { action() }, null)
    }

    fun load(player: Player) {
        region(player) { player.persistentDataContainer.get(key, PersistentDataType.STRING)?.let { enable(player, it) } }
    }

    fun enable(player: Player, requested: String? = null, kick: Boolean = true): Identity {
        identities[player.uniqueId]?.let { return it }
        val alias = requested?.takeIf { it.matches(Regex("Anon_[a-f0-9]{10}")) && available(it) }
            ?: generateSequence { "Anon_${UUID.randomUUID().toString().replace("-", "").take(10)}" }.first(::available)
        val reconnect = settings.coordinates && coordinateSessions.enable(player.uniqueId)
        val identity = Identity(player.uniqueId, player.name, if (settings.names) alias else player.name)
        identities[player.uniqueId] = identity
        region(player) {
            player.persistentDataContainer.set(key, PersistentDataType.STRING, alias)
            if (settings.names) {
                originalNames[player.uniqueId] = player.displayName() to player.playerListName()
                player.displayName(Component.text(alias))
                player.playerListName(Component.text(alias))
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
            player.persistentDataContainer.remove(key)
            restore(player)
            if (reconnect && kick) {
                player.saveData()
                player.kick(settings.messages.get("reconnect-disabled"))
            } else if (reconnect) player.sendMessage(settings.messages.get("relog-disabled"))
        }
    }

    fun forget(player: Player) {
        restore(player, false)
    }

    fun close() {
        plugin.server.onlinePlayers.forEach { region(it) { restore(it) } }
    }

    private fun restore(player: Player, refresh: Boolean = true) {
        if (identities.remove(player.uniqueId) == null) return
        originalNames.remove(player.uniqueId)?.let { (display, list) ->
            player.displayName(display)
            player.playerListName(list)
        }
        if (refresh && (settings.names || settings.skin)) refresh(player)
        if (refresh && settings.names) completions()
        if (refresh) player.updateCommands()
    }

    private fun available(alias: String): Boolean =
        plugin.server.onlinePlayers.none { it.name.equals(alias, true) } &&
            identities.values.none { it.alias.equals(alias, true) }

    private fun completions() {
        val names = plugin.server.onlinePlayers.map { it.name }
        plugin.server.onlinePlayers.forEach { viewer -> region(viewer) { viewer.setCustomChatCompletions(names) } }
    }

    private fun refresh(player: Player) {
        player.playerProfile = player.playerProfile
    }
}
