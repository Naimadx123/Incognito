package zone.vao.incognito.command

import zone.vao.incognito.identity.Identity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandNamesTest {

    private val active = Identity(UUID.randomUUID(), "ExamplePlayer", "Anon_0123456789")
    private val offline = Identity(UUID.randomUUID(), "OfflinePlayer", "Anon_9876543210")
    private val identities = listOf(active, offline)

    @Test
    fun `only online incognito names are replaced and offline names stay usable`() {
        val command = "/plugin:transfer OfflinePlayer ExamplePlayer"
        val result = CommandNames.rewrite(command, identities) { it == active.id }
        assertTrue(result.startsWith("/plugin:transfer OfflinePlayer __incognito_"))
        assertFalse(result.contains(active.realName))
        assertEquals(command, CommandNames.rewrite(command, identities) { false })
        assertEquals(command, CommandNames.rewrite(command, emptyList()) { true })
    }

    @Test
    fun `aliases labels longer names and whitespace remain unchanged`() {
        listOf(
            "/msg Anon_0123456789 hello",
            "/msg OtherPlayer hello",
            "/msg ExamplePlayerExtra hello",
            "/msg PrefixExamplePlayer hello",
            "/ExamplePlayer OtherPlayer",
            "/plugin:ExamplePlayer",
            "  /msg\tOfflinePlayer  hello  ",
            "",
        ).forEach { command ->
            assertEquals(command, CommandNames.rewrite(command, identities) { it == active.id })
        }
    }

    @Test
    fun `case quoted names selectors and message mentions share one replacement`() {
        val command = "/plugin:command \"ExamplePlayer\" @a[name=exampleplayer] EXAMPLEPLAYER!"
        val result = CommandNames.rewrite(command, identities) { it == active.id }
        val tokens = Regex("__incognito_[a-f0-9]{32}__").findAll(result).map { it.value }.toList()
        assertEquals(3, tokens.size)
        assertEquals(1, tokens.distinct().size)
        assertTrue(tokens.first().length > 16)
        assertEquals(command.replace("ExamplePlayer", tokens.first()).replace("exampleplayer", tokens.first()).replace("EXAMPLEPLAYER", tokens.first()), result)
        assertEquals(result, CommandNames.rewrite(result, identities) { true })
    }
}
