package zone.vao.incognito.identity

import org.bukkit.OfflinePlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RevealedNames {

    private val names = ConcurrentHashMap<String, String>()
    private val tokens = ConcurrentHashMap<String, String>()
    private val pattern = Regex("__incognito_realname_[a-f0-9]{32}__")

    fun placeholder(player: OfflinePlayer?, identity: Identity?): String {
        if (player?.player == null || identity == null) return ""
        val name = identity.realName
        return names.computeIfAbsent(name) {
            "__incognito_realname_${UUID.randomUUID().toString().replace("-", "")}__".also { tokens[it] = name }
        }
    }

    fun contains(text: String): Boolean = pattern.containsMatchIn(text)

    fun active(): Boolean = tokens.isNotEmpty()

    fun resolve(text: String, allowed: Boolean): String = pattern.replace(text) {
        if (allowed) tokens[it.value].orEmpty() else ""
    }
}
