package zone.vao.incognito.hook

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import zone.vao.incognito.Incognito
import zone.vao.incognito.identity.IncognitoService

class IncognitoExpansion(private val plugin: Incognito, private val service: IncognitoService) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "incognito"

    override fun getAuthor(): String = "vao"

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val identity = player?.let { service.identity(it.uniqueId) }
        return when (params.lowercase()) {
            "enabled" -> (identity != null).toString()
            "name" -> identity?.alias ?: player?.name.orEmpty()
            "realname" -> player?.name.orEmpty()
            "x", "y", "z", "world" -> {
                val location = player?.player?.location ?: return ""
                val offset = service.offset(player.uniqueId)
                when (params.lowercase()) {
                    "x" -> (location.blockX + offset.x).toString()
                    "y" -> location.blockY.toString()
                    "z" -> (location.blockZ + offset.z).toString()
                    else -> location.world.name
                }
            }
            else -> null
        }
    }
}
