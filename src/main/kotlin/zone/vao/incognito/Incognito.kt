package zone.vao.incognito

import org.bukkit.plugin.java.JavaPlugin
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import zone.vao.incognito.command.IncognitoCommand
import zone.vao.incognito.config.IncognitoConfig
import zone.vao.incognito.identity.IncognitoService
import zone.vao.incognito.identity.IncognitoListener
import zone.vao.incognito.packet.PacketMasker
import zone.vao.incognito.hook.IncognitoExpansion
import zone.vao.incognito.hook.PlaceholderMasker

class Incognito : JavaPlugin() {

    lateinit var incognitoService: IncognitoService
        private set
    private var packets: PacketMasker? = null
    private var expansion: IncognitoExpansion? = null
    private var placeholders: PlaceholderMasker? = null

    override fun onEnable() {
        IncognitoConfig.sync(this)
        reloadConfig()
        val settings = IncognitoConfig.load(config)
        incognitoService = IncognitoService(this, settings)
        packets = PacketMasker(this, incognitoService, settings).also { it.register() }
        server.pluginManager.registerEvents(IncognitoListener(incognitoService), this)
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            expansion = IncognitoExpansion(this, incognitoService).also { it.register() }
            if (settings.placeholders) placeholders = PlaceholderMasker(this, incognitoService).also { it.register() }
        }
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            it.registrar().register(IncognitoCommand.build(incognitoService), "Hides player identity", listOf("incog"))
        }
        server.onlinePlayers.forEach(incognitoService::load)
    }

    override fun onDisable() {
        packets?.close()
        placeholders?.unregister()
        expansion?.unregister()
        if (::incognitoService.isInitialized) incognitoService.close()
    }
}
