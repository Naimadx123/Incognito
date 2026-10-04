package zone.vao.incognito.config

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
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
        "join-quit-shown" to "<green>Incognito join/quit messages: shown.",
        "join-quit-hidden" to "<yellow>Incognito join/quit messages: hidden.",
        "join-quit-locked" to "<red>An administrator has locked this setting.",
        "join-message" to "<yellow><name> joined the game.",
        "quit-message" to "<yellow><name> left the game.",
        "notify-enabled" to "<gold>Incognito >> <yellow>Player <player> enabled incognito, new nickname: <name>",
        "notify-disabled" to "<gold>Incognito >> <yellow>Player <name> disabled incognito, new nickname: <player>",
        "notify-alias-changed" to "<gold>Incognito >> <yellow>Player <player> rejoined in incognito, new nickname: <name>",
        "notify-pending-enabled" to "<gold>Incognito >> <yellow>Player <player> will enable incognito after reconnecting; a nickname will be assigned at login.",
        "notify-pending-disabled" to "<gold>Incognito >> <yellow>Player <name> (<player>) will disable incognito after reconnecting.",
        "notify-cancelled" to "<gold>Incognito >> <yellow>Player <player> canceled the pending incognito change.",
        "no-permission" to "<red>You do not have permission to do that.",
        "history-loading" to "<gray>Loading incognito session history...",
        "history-busy" to "<yellow>Your previous history lookup is still running.",
        "history-empty" to "<yellow>No sessions found for <query> on page <page>.",
        "history-failed" to "<red>Session history could not be loaded. Please try again later.",
        "history-header" to "<gold>Incognito history: <query> <gray>(page <page>)",
        "history-entry" to "<yellow><name> <gray>-> <white><player> <gray>[<server>]\n<gray>UUID: <white><uuid>\n<gray>Session: <white><session>\n<gray>From <white><started> <gray>to <white><ended>",
        "history-open" to "<yellow>no end recorded",
        "history-next" to "<gray>More sessions: <yellow><command>",
    )
    private val values = defaults.mapValues { (key, default) -> config.getString("messages.$key", default)!! }

    fun get(key: String, name: String = "", player: String = "", vararg extra: TagResolver): Component =
        MiniMessage.miniMessage().deserialize(values.getValue(key), TagResolver.resolver(listOf(Placeholder.unparsed("name", name), Placeholder.unparsed("player", player)) + extra))
}
