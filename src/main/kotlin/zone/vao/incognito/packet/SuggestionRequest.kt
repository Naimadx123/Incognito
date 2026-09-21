package zone.vao.incognito.packet

import zone.vao.incognito.identity.Identity

class SuggestionRequest private constructor(
    val command: String,
    val forwarded: String,
    private val changes: List<Change>,
    private val expanded: Boolean,
) {

    private data class Change(val start: Int, val end: Int, val forwardedStart: Int, val forwardedEnd: Int)

    fun start(index: Int): Int = originalIndex(index, false)

    fun end(index: Int): Int = originalIndex(index, true)

    fun accepts(suggestion: String, start: Int, end: Int, identities: List<Identity>): Boolean {
        if (!expanded && identities.none { it.alias.equals(suggestion, true) }) return true
        return suggestion.startsWith(command.substring(start, end), true)
    }

    private fun originalIndex(index: Int, end: Boolean): Int {
        var delta = 0
        for (change in changes) {
            if (index < change.forwardedStart) break
            if (index <= change.forwardedEnd) {
                if (index == change.forwardedStart && !end) return change.start
                if (index == change.forwardedEnd) return change.end
                return change.start + (index - change.forwardedStart).coerceAtMost(change.end - change.start)
            }
            delta += change.end - change.start - (change.forwardedEnd - change.forwardedStart)
        }
        return (index + delta).coerceIn(0, command.length)
    }

    companion object {
        fun create(command: String, identities: List<Identity>, restoreCompleted: Boolean = true): SuggestionRequest {
            val changes = ArrayList<Change>()
            val result = StringBuilder()
            var cursor = 0
            var expanded = false
            Regex("\\S+").findAll(command).drop(1).forEach { token ->
                val end = token.range.last + 1
                val partial = end == command.length && identities.any { it.alias.startsWith(token.value, true) }
                val replacement = when {
                    partial -> ""
                    restoreCompleted -> identities.firstOrNull { it.alias.equals(token.value, true) }?.realName ?: return@forEach
                    else -> return@forEach
                }
                result.append(command, cursor, token.range.first)
                val start = result.length
                result.append(replacement)
                changes += Change(token.range.first, end, start, result.length)
                cursor = end
                expanded = expanded || partial
            }
            result.append(command, cursor, command.length)
            return SuggestionRequest(command, result.toString(), changes, expanded)
        }
    }
}
