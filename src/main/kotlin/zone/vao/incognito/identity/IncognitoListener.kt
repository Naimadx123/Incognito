package zone.vao.incognito.identity

import com.destroystokyo.paper.event.server.PaperServerListPingEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerEditBookEvent
import org.bukkit.event.entity.PlayerDeathEvent
import io.papermc.paper.event.player.AsyncChatEvent

class IncognitoListener(private val service: IncognitoService) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult == AsyncPlayerPreLoginEvent.Result.ALLOWED) service.claimSession(event.uniqueId)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        service.load(event.player)
        onJoinMessage(event)
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
        onQuitMessage(event)
        service.forget(event.player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onQuitMessage(event: PlayerQuitEvent) {
        event.quitMessage(service.joinQuitMessage(event.player, event.quitMessage(), false))
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDeathEarly(event: PlayerDeathEvent) {
        onDeath(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        event.deathMessage(event.deathMessage()?.let(service::systemMessage))
        event.deathScreenMessageOverride(event.deathScreenMessageOverride()?.let(service::systemMessage))
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onAsyncChatEarly(event: AsyncChatEvent) {
        protectChat(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onAsyncChat(event: AsyncChatEvent) {
        protectChat(event)
    }

    private fun protectChat(event: AsyncChatEvent) {
        if (!service.settings.names) return
        if (service.settings.maskPlayerMessages) event.message(service.publicMessage(event.message()))
        if (event.renderer() !is IncognitoChatRenderer) event.renderer(IncognitoChatRenderer(event.renderer(), service))
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
