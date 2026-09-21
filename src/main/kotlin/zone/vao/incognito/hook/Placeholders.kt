package zone.vao.incognito.hook

import org.bukkit.OfflinePlayer
import zone.vao.incognito.identity.IncognitoService
import zone.vao.incognito.identity.RevealedNames

class Placeholders(private val service: IncognitoService) {

    val keys = listOf("enabled", "name", "realname", "x", "y", "z", "world")

    fun value(player: OfflinePlayer?, params: String): String? {
        val identity = player?.let { service.identity(it.uniqueId) }
        return when (params.lowercase()) {
            "enabled" -> (identity != null).toString()
            "name" -> identity?.alias ?: player?.name.orEmpty()
            "realname" -> RevealedNames.placeholder(player, identity)
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
