package zone.vao.incognito.config

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.configuration.file.FileConfiguration

class Messages(config: FileConfiguration) {

    private val defaults = mapOf(
        "enabled" to "<green>Incognito enabled. Your name: <name>",
        "disabled" to "<yellow>Incognito disabled.",
        "status-enabled" to "<green>Incognito: <name>",
        "status-disabled" to "<yellow>Incognito is disabled.",
        "players-only" to "<red>This command is only available to players.",
        "reconnect-enabled" to "<yellow>Incognito enabled. Reconnect to load the world with shifted coordinates.",
        "reconnect-disabled" to "<yellow>Incognito disabled. Reconnect to restore real coordinates.",
        "reconnect-required" to "<yellow>Reconnect to start the coordinate offset.",
        "unsupported" to "<red>Incognito protection cannot run on this server version.",
        "admin-enabled" to "<green>Incognito enabled for <player>. Name: <name>",
        "admin-disabled" to "<yellow>Incognito disabled for <player>.",
        "relog-enabled" to "<yellow>Incognito was enabled by an administrator. Coordinates change after you reconnect.",
        "relog-disabled" to "<yellow>Incognito was disabled by an administrator. Coordinates return to normal after you reconnect.",
    )
    private val values = defaults.mapValues { (key, default) -> config.getString("messages.$key", default)!! }

    fun get(key: String, name: String = "", player: String = ""): Component =
        MiniMessage.miniMessage().deserialize(values.getValue(key), Placeholder.unparsed("name", name), Placeholder.unparsed("player", player))
}
