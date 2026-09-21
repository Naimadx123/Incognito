package zone.vao.incognito.packet

import zone.vao.incognito.identity.Identity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionRequestTest {

    private val identity = Identity(UUID.randomUUID(), "ExamplePlayer", "Anon_0123456789")
    private val identities = listOf(identity)

    @Test
    fun `plugin completion expands alias prefixes without restoring previous alias arguments`() {
        val command = "/plugin:command ${identity.alias} Anon_01"
        val request = SuggestionRequest.create(command, identities, false)
        assertEquals("/plugin:command ${identity.alias} ", request.forwarded)
        val start = request.start(request.forwarded.length)
        val end = request.end(request.forwarded.length)
        assertEquals(command.lastIndexOf("Anon_01"), start)
        assertEquals(command.length, end)
        assertTrue(request.accepts(identity.alias, start, end, identities))
        assertFalse(request.accepts("OtherPlayer", start, end, identities))
    }

    @Test
    fun `plugin message completion masks custom nicknames before matching the alias prefix`() {
        val request = SuggestionRequest.create("/plugin:msg Anon_01", identities, false)
        assertEquals("/plugin:msg ", request.forwarded)
        val nickname = identity.copy(realName = "~CustomNickname")
        val names = identities + nickname
        val suggestion = TextMasker(emptyList()).suggestion("~CustomNickname", names, zone.vao.incognito.coordinate.CoordinateOffset.ZERO, 0)
        assertEquals(identity.alias, suggestion)
        assertTrue(request.accepts(suggestion, request.start(request.forwarded.length), request.end(request.forwarded.length), names))
        val hidden = SuggestionRequest.create("/msg ~Custom", identities, false)
        assertFalse(hidden.accepts(suggestion, 5, hidden.command.length, names))
    }

    @Test
    fun `expands alias prefixes and restores the replacement range`() {
        val request = SuggestionRequest.create("/tp Anon_01", identities)
        assertEquals("/tp ", request.forwarded)
        assertEquals(4, request.start(4))
        assertEquals(11, request.end(4))
        assertTrue(request.accepts(identity.alias, 4, 11, identities))
        assertFalse(request.accepts("OtherPlayer", 4, 11, identities))
    }

    @Test
    fun `translates completed arguments before requesting the next argument`() {
        val command = "/tp ${identity.alias} Anon_"
        val request = SuggestionRequest.create(command, identities)
        assertEquals("/tp ExamplePlayer ", request.forwarded)
        assertEquals(20, request.start(request.forwarded.length))
        assertEquals(command.length, request.end(request.forwarded.length))
        assertTrue(request.accepts(identity.alias, 20, command.length, identities))
    }

    @Test
    fun `maps ranges after aliases with different lengths`() {
        val command = "/tp ${identity.alias} Other"
        val request = SuggestionRequest.create(command, identities)
        assertEquals("/tp ExamplePlayer Other", request.forwarded)
        assertEquals(command.indexOf("Other"), request.start(request.forwarded.indexOf("Other")))
        assertEquals(command.length, request.end(request.forwarded.length))
    }

    @Test
    fun `does not expose aliases through matching real names`() {
        val request = SuggestionRequest.create("/tp Example", identities)
        assertEquals(request.command, request.forwarded)
        assertFalse(request.accepts(identity.alias, 4, 11, identities))
        assertTrue(request.accepts("ExampleOther", 4, 11, identities))
    }

    @Test
    fun `keeps empty prefixes and disabled name completion intact`() {
        val empty = SuggestionRequest.create("/tp ", identities)
        assertEquals(empty.command, empty.forwarded)
        assertTrue(empty.accepts(identity.alias, 4, 4, identities))
        val disabled = SuggestionRequest.create("/tp ${identity.alias}", emptyList())
        assertEquals(disabled.command, disabled.forwarded)
        assertEquals(disabled.command.length, disabled.end(disabled.forwarded.length))
    }
}
