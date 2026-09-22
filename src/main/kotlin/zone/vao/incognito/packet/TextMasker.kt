package zone.vao.incognito.packet

import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.identity.Identity

class TextMasker(private val patterns: List<Regex>) {

    private val number = Regex("-?\\d+(?:\\.\\d+)?")

    private data class Names(val pattern: Regex, val aliases: Map<String, String>)

    private val cache = object : LinkedHashMap<List<Identity>, Names>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Identity>, Names>): Boolean = size > 16
    }
    @Volatile private var last: Pair<List<Identity>, Names>? = null

    fun mask(text: String, identities: List<Identity>, offset: CoordinateOffset): String {
        var result = text
        if (identities.isNotEmpty()) {
            val names = names(identities)
            result = names.pattern.replace(result) { names.aliases.getValue(it.value.lowercase()) }
        }
        if (offset != CoordinateOffset.ZERO) patterns.forEach { pattern -> result = pattern.replace(result) { shift(it, offset) } }
        return result
    }

    fun exposes(text: String, identities: List<Identity>): Boolean = identities.isNotEmpty() && names(identities).pattern.containsMatchIn(text)

    private fun names(identities: List<Identity>): Names {
        last?.let { if (it.first === identities) return it.second }
        return synchronized(cache) {
            val compiled = cache.getOrPut(identities.toList()) {
                Names(
                    Regex("(?<![A-Za-z0-9_])(?:${identities.joinToString("|") { Regex.escape(it.realName) }})(?![A-Za-z0-9_])", RegexOption.IGNORE_CASE),
                    identities.associate { it.realName.lowercase() to it.alias },
                )
            }
            last = identities to compiled
            compiled
        }
    }

    fun restore(text: String, identities: List<Identity>, offset: CoordinateOffset): String =
        mask(text, identities.map { it.copy(realName = it.alias, alias = it.realName) }, offset.inverse())

    fun suggestion(text: String, identities: List<Identity>, offset: CoordinateOffset, preceding: Int): String {
        val parts = text.split(' ')
        if (offset == CoordinateOffset.ZERO || !parts.all { it.matches(number) }) return mask(text, identities, offset)
        return parts.mapIndexed { index, part ->
            when ((preceding + index) % 3) {
                0 -> shift(part, offset.x)
                2 -> shift(part, offset.z)
                else -> part
            }
        }.joinToString(" ")
    }

    fun axis(text: String, offset: CoordinateOffset, axis: String): String? = when {
        !text.matches(number) -> null
        axis == "x" -> shift(text, offset.x)
        axis == "z" -> shift(text, offset.z)
        else -> null
    }

    fun preceding(command: String): Int = command.trimEnd().split(' ').takeLastWhile { it.matches(number) }.size

    private fun shift(value: String, delta: Int): String = value.toBigDecimal().add(delta.toBigDecimal()).toPlainString()

    private fun shift(match: MatchResult, offset: CoordinateOffset): String {
        val builder = StringBuilder(match.value)
        listOf("x" to offset.x, "z" to offset.z)
            .mapNotNull { (axis, delta) -> runCatching { match.groups[axis] }.getOrNull()?.let { it to delta } }
            .sortedByDescending { it.first.range.first }
            .forEach { (group, delta) ->
                val start = group.range.first - match.range.first
                builder.replace(start, start + group.value.length, shift(group.value, delta))
            }
        return builder.toString()
    }
}
