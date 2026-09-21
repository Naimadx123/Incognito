package zone.vao.incognito.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SessionAliasesTest {

    @Test
    fun `reconnecting generates a new alias and skips unavailable names`() {
        val aliases = SessionAliases("Anon_{random}")
        val first = aliases.next(null) { true }
        var rejected: String? = null
        val second = aliases.next(first) {
            if (rejected == null) {
                rejected = it
                false
            } else true
        }
        assertNotEquals(first, second)
        assertNotEquals(rejected, second)
        assertTrue(first.matches(Regex("Anon_[a-f0-9]{10}")))
        assertTrue(second.matches(Regex("Anon_[a-f0-9]{10}")))
    }

    @Test
    fun `configured alias format stays within the player name limit`() {
        val alias = SessionAliases("Hidden_{random}").next(null) { true }
        assertEquals(16, alias.length)
        assertTrue(alias.startsWith("Hidden_"))
    }
}
