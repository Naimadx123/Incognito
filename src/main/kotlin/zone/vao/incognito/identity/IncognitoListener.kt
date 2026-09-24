package zone.vao.incognito.identity

import com.destroystokyo.paper.event.server.PaperServerListPingEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerEditBookEvent

class IncognitoListener(private val service: IncognitoService) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        service.load(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onJoinMessage(event: PlayerJoinEvent) {
        event.joinMessage(service.joinQuitMessage(event.player, event.joinMessage(), true))
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPing(event: PaperServerListPingEvent) {
        if (!service.settings.names) return
        event.listedPlayers.replaceAll { listed ->
            service.identity(listed.id())?.let { PaperServerListPingEvent.ListedPlayerInfo(it.alias, it.maskedId) } ?: listed
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onQuit(event: PlayerQuitEvent) {
        event.quitMessage(service.joinQuitMessage(event.player, event.quitMessage(), false))
        service.forget(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBookSign(event: PlayerEditBookEvent) {
        if (!event.isSigning || !service.settings.names || !service.settings.books) return
        val identity = service.identity(event.player.uniqueId) ?: return
        val meta = event.newBookMeta
        meta.author = identity.alias
        event.newBookMeta = meta
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val command = service.rewriteCommand(event.message)
        if (command != event.message) event.message = command
    }
}
