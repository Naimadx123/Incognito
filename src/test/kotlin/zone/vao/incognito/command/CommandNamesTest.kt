package zone.vao.incognito.command

import zone.vao.incognito.identity.Identity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandNamesTest {

    private val active = Identity(UUID.randomUUID(), "Notch", "Anon_0123456789")
    private val offline = Identity(UUID.randomUUID(), "OfflinePlayer", "Anon_9876543210")
    private val identities = listOf(active, offline)

    @Test
    fun `all commands rewrite hidden online names without command definitions`() {
        for (command in listOf("/msg Notch hey", "/essentials:msg Notch hey", "/custom-command invite Notch", "/plugin:arbitrary subcommand Notch")) {
            val rewritten = rewrite(command)
            assertFalse(rewritten.contains("Notch"))
            assertTrue(rewritten.contains("__incognito_"))
            assertEquals(command.substringBefore(' '), rewritten.substringBefore(' '))
        }
    }

    @Test
    fun `every occurrence in command arguments including message text is rewritten consistently`() {
        val command = "/msg Notch hey nOtCh and NOTCH!"
        val rewritten = rewrite(command)
        val tokens = Regex("__incognito_[a-f0-9]{32}_[A-Za-z0-9_]{1,16}__").findAll(rewritten).map { it.value }.toList()
        assertEquals(3, tokens.size)
        assertEquals(1, tokens.distinct().size)
        assertTrue(tokens.first().length > 16)
        assertEquals("/msg ${tokens.first()} hey ${tokens.first()} and ${tokens.first()}!", rewritten)
        assertEquals(rewritten, rewrite(rewritten))
        assertFalse(rewrite("/msg OtherPlayer hey Notch").contains("Notch"))
    }

    @Test
    fun `normal chat offline players aliases and longer unrelated names remain unchanged`() {
        for (command in listOf("hey Notch", "Notch", "/msg OfflinePlayer hey", "/msg Anon_0123456789 hey", "/msg NotchExtra hey", "/msg PrefixNotch hey", "/Notch OtherPlayer", "/Notch", "/msg", "")) {
            assertEquals(command, rewrite(command))
        }
        assertEquals("/msg Notch hey", CommandNames.rewrite("/msg Notch hey", identities) { false })
        assertEquals("/msg Notch hey", CommandNames.rewrite("/msg Notch hey", emptyList()) { true })
    }

    @Test
    fun `quotes selectors and original whitespace are preserved`() {
        val rewritten = rewrite("/custom\t\"Notch\"  @a[name=Notch]  ")
        val tokens = Regex("__incognito_[a-f0-9]{32}_[A-Za-z0-9_]{1,16}__").findAll(rewritten).map { it.value }.toList()
        assertEquals(2, tokens.size)
        assertEquals("/custom\t\"${tokens.first()}\"  @a[name=${tokens.first()}]  ", rewritten)
    }

    private fun rewrite(command: String): String = CommandNames.rewrite(command, identities) { it == active.id }
}
