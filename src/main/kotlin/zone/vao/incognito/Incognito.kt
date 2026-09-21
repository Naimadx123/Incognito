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
import zone.vao.incognito.hook.IncognitoMiniPlaceholders
import zone.vao.incognito.storage.PlayerDataService
import zone.vao.incognito.storage.StorageConfig
import zone.vao.incognito.storage.StorageFactory

class Incognito : JavaPlugin() {

    lateinit var incognitoService: IncognitoService
        private set
    private var packets: PacketMasker? = null
    private var expansion: IncognitoExpansion? = null
    private var placeholders: PlaceholderMasker? = null
    private var miniPlaceholders: IncognitoMiniPlaceholders? = null
    private var data: PlayerDataService? = null

    override fun onEnable() {
        IncognitoConfig.sync(this)
        reloadConfig()
        val settings = IncognitoConfig.load(config)
        val storage = PlayerDataService(StorageFactory.create(dataFolder, StorageConfig.load(config)), logger)
        data = storage
        incognitoService = IncognitoService(this, settings, storage)
        packets = PacketMasker(this, incognitoService, settings).also { it.register() }
        server.pluginManager.registerEvents(IncognitoListener(incognitoService), this)
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            expansion = IncognitoExpansion(this, incognitoService).also { it.register() }
            if (settings.placeholders) placeholders = PlaceholderMasker(this, incognitoService).also { it.register() }
        }
        if (server.pluginManager.isPluginEnabled("MiniPlaceholders")) {
            miniPlaceholders = IncognitoMiniPlaceholders(this, incognitoService).also { it.register() }
        }
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            it.registrar().register(IncognitoCommand.build(incognitoService), "Hides player identity", listOf("incog"))
        }
        server.onlinePlayers.forEach(incognitoService::load)
    }

    override fun onDisable() {
        try {
            packets?.close()
            miniPlaceholders?.unregister()
            placeholders?.unregister()
            expansion?.unregister()
            if (::incognitoService.isInitialized) incognitoService.close()
        } finally {
            data?.close()
        }
    }
}
