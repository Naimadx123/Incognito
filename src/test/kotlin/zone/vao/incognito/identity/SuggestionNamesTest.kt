package zone.vao.incognito.identity

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.packet.SuggestionRequest
import zone.vao.incognito.packet.TextMasker
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionNamesTest {

    private val identity = Identity(UUID.randomUUID(), "ExamplePlayer", "Anon_0123456789")
    private val cache = SuggestionNames()
    private val text = TextMasker(emptyList())

    @Test
    fun `account display and tab names complete to the same alias`() {
        cache.update(identity.id, Component.text("~DisplayNickname", NamedTextColor.GREEN), Component.text("TabNickname", NamedTextColor.GOLD))
        val names = cache.identities(listOf(identity))
        val request = SuggestionRequest.create("/plugin:msg Anon_01", listOf(identity), false)
        for (name in listOf(identity.realName, "~DisplayNickname", "TabNickname")) {
            val suggestion = text.suggestion(name, names, CoordinateOffset.ZERO, 0)
            assertEquals(identity.alias, suggestion)
            assertTrue(request.accepts(suggestion, request.start(request.forwarded.length), request.end(request.forwarded.length), names))
        }
    }

    @Test
    fun `refresh replaces current names while retaining supplied original names`() {
        val original = Component.text("OriginalNickname")
        cache.update(identity.id, original, Component.text("FirstNickname"))
        cache.update(identity.id, original, Component.text("SecondNickname"))
        val names = cache.identities(listOf(identity))
        assertEquals("${identity.alias} FirstNickname ${identity.alias}", text.mask("OriginalNickname FirstNickname SecondNickname", names, CoordinateOffset.ZERO))
        cache.remove(identity.id)
        assertEquals(listOf(identity), cache.identities(listOf(identity)))
    }

    @Test
    fun `ambiguous nicknames never resolve to an arbitrary player`() {
        val other = Identity(UUID.randomUUID(), "OtherPlayer", "Anon_9876543210")
        cache.update(identity.id, Component.text("SharedNickname"), Component.text(other.realName))
        cache.update(other.id, Component.text("sharednickname"))
        val names = cache.identities(listOf(identity, other))
        assertFalse(names.any { it.realName.equals("SharedNickname", true) })
        assertEquals(other.alias, text.mask(other.realName, names, CoordinateOffset.ZERO))
    }

    @Test
    fun `longer tab names are masked before embedded account names`() {
        cache.update(identity.id, Component.text("[Rank] ExamplePlayer"), Component.empty(), Component.text(identity.alias))
        val names = cache.identities(listOf(identity))
        assertEquals(identity.alias, text.mask("[Rank] ExamplePlayer", names, CoordinateOffset.ZERO))
        assertTrue(cache.identities(emptyList()).isEmpty())
    }
}
