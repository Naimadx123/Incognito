package zone.vao.incognito.command

import zone.vao.incognito.identity.Identity
import java.util.UUID

object CommandNames {

    private val label = Regex("^/[^\\s/]+\\s+")
    private val name = Regex("[A-Za-z0-9_]+")
    private val token = Regex("__incognito_[a-f0-9]{32}__", RegexOption.IGNORE_CASE)

    fun contains(text: String): Boolean = token.containsMatchIn(text)

    fun resolve(text: String): String = token.replace(text, "unknown")

    fun rewrite(command: String, identities: Collection<Identity>, online: (UUID) -> Boolean): String {
        if (identities.isEmpty()) return command
        val start = label.find(command)?.range?.last?.plus(1) ?: return command
        val hidden = identities.filter { online(it.id) }.map { it.realName.lowercase() }.toSet()
        if (hidden.isEmpty()) return command
        val replacements = mutableMapOf<String, String>()
        val arguments = name.replace(command.substring(start)) {
            val key = it.value.lowercase()
            if (key in hidden) replacements.getOrPut(key) { "__incognito_${UUID.randomUUID().toString().replace("-", "")}__" } else it.value
        }
        return command.take(start) + arguments
    }
}
