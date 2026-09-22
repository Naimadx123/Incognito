package zone.vao.incognito.packet

import kotlin.test.Test
import kotlin.test.assertEquals
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.identity.Identity
import java.util.UUID

class TextMaskerTest {

    private val identity = Identity(UUID.randomUUID(), "ExamplePlayer", "Anon_0123456789")
    private val offset = CoordinateOffset(32_000, -64_000)
    private val masker = TextMasker(listOf(
        Regex("\\bx\\s*[:=]\\s*(?<x>-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
        Regex("\\bz\\s*[:=]\\s*(?<z>-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
        Regex("(?<x>-?\\d+),\\s*-?\\d+,\\s*(?<z>-?\\d+)"),
    ))

    @Test
    fun `cached masks follow session changes and do not retain names after departure`() {
        val first = listOf(identity)
        val next = listOf(Identity(identity.id, identity.realName, "Anon_9876543210"))
        assertEquals(identity.alias, masker.mask(identity.realName, first, CoordinateOffset.ZERO))
        assertEquals(next.single().alias, masker.mask(identity.realName, next, CoordinateOffset.ZERO))
        assertEquals(identity.realName, masker.mask(identity.realName, emptyList(), CoordinateOffset.ZERO))
        assertEquals(identity.alias, masker.mask(identity.realName, first, CoordinateOffset.ZERO))
        assertEquals("ExamplePlayer", masker.restore(identity.alias, first, CoordinateOffset.ZERO))
        assertEquals(identity.alias, masker.mask(identity.realName, first, CoordinateOffset.ZERO))
    }

    @Test
    fun `masks names without changing similar player names`() {
        assertEquals("Anon_0123456789 ExamplePlayerPL xExamplePlayer", masker.mask("eXaMpLePlAyEr ExamplePlayerPL xExamplePlayer", listOf(identity), CoordinateOffset.ZERO))
    }

    @Test
    fun `shifts negative and decimal coordinates without touching height or ordinary numbers`() {
        assertEquals("X: 31879.5 z=-63679 level 42 31990, 64, -63990", masker.mask("X: -120.5 z=321 level 42 -10, 64, 10", emptyList(), offset))
    }

    @Test
    fun `keeps coordinates without offset`() {
        assertEquals("Anon_0123456789 X: 123", masker.mask("ExamplePlayer X: 123", listOf(identity), CoordinateOffset.ZERO))
    }

    @Test
    fun `restores aliases and shifted coordinates typed by the client`() {
        assertEquals("tp ExamplePlayer X: 10", masker.restore("tp Anon_0123456789 X: 32010", listOf(identity), offset))
    }

    @Test
    fun `numeric suggestions follow the axis order of the typed command`() {
        assertEquals("32001 64 -63997", masker.suggestion("1 64 3", emptyList(), offset, masker.preceding("/tp ")))
        assertEquals("64 -63997", masker.suggestion("64 3", emptyList(), offset, masker.preceding("/tp 1 ")))
        assertEquals("-63997", masker.suggestion("3", emptyList(), offset, masker.preceding("/fill 1 2 3 4 5 ")))
        assertEquals("Anon_0123456789", masker.suggestion("ExamplePlayer", listOf(identity), offset, 0))
    }

    @Test
    fun `placeholder axis values are shifted only when numeric`() {
        assertEquals("32010", masker.axis("10", offset, "x"))
        assertEquals("-63990.5", masker.axis("9.5", offset, "z"))
        assertEquals(null, masker.axis("10", offset, "y"))
        assertEquals(null, masker.axis("north", offset, "x"))
    }

    @Test
    fun `patterns without axis groups and aliases are left alone`() {
        val other = Identity(UUID.randomUUID(), identity.alias, "Anon_aaaaaaaaaa")
        assertEquals(identity.alias, masker.mask(identity.realName, listOf(identity, other), CoordinateOffset.ZERO))
        assertEquals("123", TextMasker(listOf(Regex("123"))).mask("123", emptyList(), offset))
    }
}
