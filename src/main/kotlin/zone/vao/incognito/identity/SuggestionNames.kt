package zone.vao.incognito.identity

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SuggestionNames {

    private val names = ConcurrentHashMap<UUID, List<String>>()
    private val plain = PlainTextComponentSerializer.plainText()

    fun update(id: UUID, vararg components: Component?) {
        names[id] = components.filterNotNull().map { plain.serialize(it) }.filter { it.isNotBlank() && !RevealedNames.contains(it) }.distinct()
    }

    fun remove(id: UUID) {
        names.remove(id)
    }

    fun identities(identities: List<Identity>): List<Identity> {
        val reserved = identities.flatMap { listOf(it.realName.lowercase(), it.alias.lowercase()) }.toSet()
        val additional = identities.flatMap { identity ->
            names[identity.id].orEmpty().filterNot { it.lowercase() in reserved }.map { identity.copy(realName = it) }
        }.groupBy { it.realName.lowercase() }.values.filter { matches -> matches.map { it.id }.distinct().size == 1 }.map { it.first() }
        return (identities + additional).sortedByDescending { it.realName.length }
    }
}
