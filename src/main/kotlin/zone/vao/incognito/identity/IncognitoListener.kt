package zone.vao.incognito.identity

import com.destroystokyo.paper.event.server.PaperServerListPingEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class IncognitoListener(private val service: IncognitoService) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        service.load(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPing(event: PaperServerListPingEvent) {
        if (!service.settings.names) return
        event.listedPlayers.replaceAll { listed ->
            service.identity(listed.id())?.let { PaperServerListPingEvent.ListedPlayerInfo(it.alias, it.id) } ?: listed
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onQuit(event: PlayerQuitEvent) {
        if (service.identity(event.player.uniqueId) != null) event.quitMessage(null)
        service.forget(event.player)
    }
}
