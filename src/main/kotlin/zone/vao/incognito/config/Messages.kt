package zone.vao.incognito.config

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.configuration.file.FileConfiguration

class Messages(config: FileConfiguration) {

    private val defaults = mapOf(
        "status-enabled" to "<green>Incognito: <name>",
        "status-disabled" to "<yellow>Incognito is disabled.",
        "pending-enabled" to "<yellow>Incognito will be enabled after you reconnect. Please log out and join again when it is safe to do so.",
        "pending-disabled" to "<yellow>Incognito will be disabled after you reconnect. Please log out and join again when it is safe to do so.",
        "admin-pending-enabled" to "<yellow>Incognito will be enabled for <player> after they reconnect.",
        "admin-pending-disabled" to "<yellow>Incognito will be disabled for <player> after they reconnect.",
        "players-only" to "<red>This command is only available to players.",
        "reconnect-required" to "<yellow>Reconnect to initialize Incognito protection.",
        "unsupported" to "<red>Incognito protection cannot run on this server version.",
        "admin-enabled" to "<green>Incognito enabled for <player>. The player must reconnect.",
        "admin-disabled" to "<yellow>Incognito disabled for <player>.",
    )
    private val values = defaults.mapValues { (key, default) -> config.getString("messages.$key", default)!! }

    fun get(key: String, name: String = "", player: String = ""): Component =
        MiniMessage.miniMessage().deserialize(values.getValue(key), Placeholder.unparsed("name", name), Placeholder.unparsed("player", player))
}
