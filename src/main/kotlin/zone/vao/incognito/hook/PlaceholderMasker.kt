package zone.vao.incognito.hook

import me.clip.placeholderapi.PlaceholderAPIPlugin
import me.clip.placeholderapi.events.ExpansionRegisterEvent
import me.clip.placeholderapi.expansion.Cacheable
import me.clip.placeholderapi.expansion.Cleanable
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import me.clip.placeholderapi.expansion.Relational
import me.clip.placeholderapi.expansion.Taskable
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import zone.vao.incognito.Incognito
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.identity.IncognitoService
import zone.vao.incognito.packet.TextMasker

class PlaceholderMasker(private val plugin: Incognito, private val service: IncognitoService) : Listener {

    private val text = TextMasker(service.settings.coordinatePatterns)

    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        PlaceholderAPIPlugin.getInstance().localExpansionManager.expansions.toList().forEach { expansion ->
            if (wrappable(expansion) && expansion.unregister()) Wrapped(expansion).register()
        }
    }

    fun unregister() {
        HandlerList.unregisterAll(this)
        PlaceholderAPIPlugin.getInstance().localExpansionManager.expansions.filterIsInstance<Wrapped>().forEach { wrapped ->
            if (wrapped.unregister()) wrapped.delegate.register()
        }
    }

    @EventHandler
    fun onRegister(event: ExpansionRegisterEvent) {
        if (!wrappable(event.expansion)) return
        event.isCancelled = true
        Wrapped(event.expansion).register()
    }

    private fun wrappable(expansion: PlaceholderExpansion): Boolean = expansion !is Wrapped && expansion !is IncognitoExpansion

    private fun obfuscate(player: OfflinePlayer?, params: String, result: String?): String? {
        if (result == null) return null
        val identities = if (service.settings.names) service.identities() else emptyList()
        val offset = player?.let { service.offset(it.uniqueId) } ?: CoordinateOffset.ZERO
        if (identities.isEmpty() && offset == CoordinateOffset.ZERO) return result
        return text.axis(result, offset, params.substringAfterLast('_').lowercase()) ?: text.mask(result, identities, offset)
    }

    private inner class Wrapped(val delegate: PlaceholderExpansion) : PlaceholderExpansion(), Relational, Taskable, Cacheable, Cleanable {

        init {
            expansionType = delegate.expansionType
        }

        override fun getIdentifier(): String = delegate.identifier

        override fun getAuthor(): String = delegate.author

        override fun getVersion(): String = delegate.version

        override fun getName(): String = delegate.name

        override fun getRequiredPlugin(): String? = delegate.requiredPlugin

        override fun getPlaceholders(): List<String> = delegate.placeholders

        override fun persist(): Boolean = delegate.persist()

        override fun canRegister(): Boolean = delegate.canRegister()

        override fun onRequest(player: OfflinePlayer?, params: String): String? = obfuscate(player, params, delegate.onRequest(player, params))

        override fun onPlaceholderRequest(one: Player?, two: Player?, params: String): String? =
            (delegate as? Relational)?.let { obfuscate(one, params, it.onPlaceholderRequest(one, two, params)) }

        override fun start() {
            (delegate as? Taskable)?.start()
            if (delegate is Listener) this@PlaceholderMasker.plugin.server.pluginManager.registerEvents(delegate, PlaceholderAPIPlugin.getInstance())
        }

        override fun stop() {
            (delegate as? Taskable)?.stop()
            if (delegate is Listener) HandlerList.unregisterAll(delegate)
        }

        override fun clear() {
            (delegate as? Cacheable)?.clear()
        }

        override fun cleanup(player: Player) {
            (delegate as? Cleanable)?.cleanup(player)
        }
    }
}
