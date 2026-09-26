package zone.vao.incognito.hook

import de.bluecolored.bluemap.api.BlueMapAPI
import org.bukkit.entity.Player
import org.dynmap.DynmapCommonAPI
import xyz.jpenilla.squaremap.api.SquaremapProvider
import zone.vao.incognito.Incognito
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MapHooks(private val plugin: Incognito) {

    private interface MapHook {
        fun visible(player: Player, visible: Boolean)
    }

    private val hidden = ConcurrentHashMap.newKeySet<UUID>()
    private val hooks: List<Pair<String, MapHook>> = listOfNotNull(
        hook("dynmap") { dynmap() },
        hook("BlueMap") { blueMap() },
        hook("squaremap") { squaremap() },
        hook("Pl3xMap") { pl3xMap() },
    )

    init {
        if (hooks.isNotEmpty()) plugin.logger.info("Hiding incognito players on: ${hooks.joinToString { it.first }}.")
    }

    fun hide(player: Player) {
        if (hooks.isEmpty()) return
        hidden.add(player.uniqueId)
        apply(player, false)
        player.scheduler.runDelayed(plugin, { if (player.uniqueId in hidden) apply(player, false) }, null, 40)
    }

    fun show(player: Player) {
        if (hidden.remove(player.uniqueId)) apply(player, true)
    }

    fun close() {
        plugin.server.onlinePlayers.filter { it.uniqueId in hidden }.forEach(::show)
        hidden.clear()
    }

    private fun apply(player: Player, visible: Boolean) {
        hooks.forEach { (name, hook) ->
            runCatching { hook.visible(player, visible) }
                .onFailure { plugin.logger.warning("Cannot change the visibility of ${player.uniqueId} on $name (${it.javaClass.simpleName}).") }
        }
    }

    private fun hook(name: String, create: () -> MapHook): Pair<String, MapHook>? {
        if (!plugin.server.pluginManager.isPluginEnabled(name)) return null
        return runCatching { name to create() }
            .onFailure { plugin.logger.warning("Cannot hook into $name (${it.javaClass.simpleName}); incognito players stay visible there.") }
            .getOrNull()
    }

    private fun dynmap(): MapHook {
        val api = plugin.server.pluginManager.getPlugin("dynmap") as DynmapCommonAPI
        return object : MapHook {
            override fun visible(player: Player, visible: Boolean) = api.assertPlayerInvisibility(player.name, !visible, plugin.name)
        }
    }

    private fun blueMap(): MapHook = object : MapHook {
        override fun visible(player: Player, visible: Boolean) {
            BlueMapAPI.getInstance().ifPresent { it.webApp.setPlayerVisibility(player.uniqueId, visible) }
        }
    }

    private fun squaremap(): MapHook = object : MapHook {
        override fun visible(player: Player, visible: Boolean) {
            val players = SquaremapProvider.get().playerManager()
            if (visible) players.show(player.uniqueId, false) else players.hide(player.uniqueId, false)
        }
    }

    private fun pl3xMap(): MapHook {
        val api = Class.forName("net.pl3x.map.core.Pl3xMap").getMethod("api")
        val registry = api.returnType.getMethod("getPlayerRegistry")
        val get = registry.returnType.getMethod("get", UUID::class.java)
        val setHidden = Class.forName("net.pl3x.map.core.player.Player").getMethod("setHidden", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        return object : MapHook {
            override fun visible(player: Player, visible: Boolean) {
                val registered = get.invoke(registry.invoke(api.invoke(null)), player.uniqueId) ?: return
                setHidden.invoke(registered, !visible, false)
            }
        }
    }
}
