package zone.vao.incognito.identity

import java.util.UUID

class SessionAliases(private val format: String) {

    private val length = minOf(10, 16 - format.replace("{random}", "").length)

    fun next(previous: String?, available: (String) -> Boolean): String =
        generateSequence { format.replace("{random}", UUID.randomUUID().toString().replace("-", "").take(length)) }
            .first { it != previous && available(it) }
}
