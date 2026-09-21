package zone.vao.incognito.hook

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import zone.vao.incognito.Incognito
import zone.vao.incognito.identity.IncognitoService

class IncognitoExpansion(private val plugin: Incognito, service: IncognitoService) : PlaceholderExpansion() {

    private val placeholders = Placeholders(service)

    override fun getIdentifier(): String = "incognito"

    override fun getAuthor(): String = "vao"

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? = placeholders.value(player, params)
}
