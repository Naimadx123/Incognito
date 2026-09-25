package zone.vao.incognito.identity

import java.util.UUID

class SessionAliases(private val format: String) {

    init {
        require(format.matches(Regex("[A-Za-z0-9_]*\\{random\\}[A-Za-z0-9_]*")) && format.length <= 23) {
            "names.format must contain exactly one {random} and at most 15 other letters, digits or underscores"
        }
    }

    private val length = minOf(10, 16 - format.replace("{random}", "").length)

    fun next(previous: String?, available: (String) -> Boolean): String {
        repeat(1024) {
            val alias = format.replace("{random}", UUID.randomUUID().toString().replace("-", "").take(length))
            if (alias != previous && available(alias)) return alias
        }
        error("Cannot allocate a free incognito alias; increase the random part of names.format")
    }
}
