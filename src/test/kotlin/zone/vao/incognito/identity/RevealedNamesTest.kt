package zone.vao.incognito.identity

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.packet.ComponentMasker
import zone.vao.incognito.packet.TextMasker
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RevealedNamesTest {

    private val identity = Identity(UUID.randomUUID(), "ExamplePlayer", "Anon_0123456789")
    private val masker = ComponentMasker(TextMasker(emptyList()))

    @Test
    fun `real name placeholder is empty when the target is not incognito`() {
        for (targetPermission in listOf(false, true)) {
            val target = player(targetPermission)
            val placeholder = RevealedNames.placeholder(target, null)
            assertEquals("", placeholder)
            for (viewerPermission in listOf(false, true)) {
                assertEquals(
                    Component.text("${identity.realName} "),
                    masker.mask(Component.text("${identity.realName} $placeholder"), emptyList(), CoordinateOffset.ZERO, viewerPermission),
                )
            }
            val enabled = RevealedNames.placeholder(target, identity)
            assertEquals(identity.realName, RevealedNames.resolve(enabled, true))
            assertEquals("", RevealedNames.resolve(enabled, false))
            assertEquals("", RevealedNames.placeholder(target, null))
        }
    }

    @Test
    fun `keeps ordinary names and metadata masked for permitted recipients`() {
        val other = Identity(UUID.randomUUID(), "OtherPlayer", "Anon_9876543210")
        val identities = listOf(identity, other)
        val component = Component.text("${identity.alias} / ${other.alias}", NamedTextColor.GREEN)
            .hoverEvent(HoverEvent.showText(Component.text(other.alias)))
            .clickEvent(ClickEvent.suggestCommand("/tp ${identity.alias}"))
        val expected = Component.text("ExamplePlayer / OtherPlayer", NamedTextColor.GREEN)
            .hoverEvent(HoverEvent.showText(Component.text("OtherPlayer")))
            .clickEvent(ClickEvent.suggestCommand("/tp ExamplePlayer"))
        assertEquals(component, masker.mask(component, identities, CoordinateOffset.ZERO, true))
        assertEquals(component, masker.mask(expected, identities, CoordinateOffset.ZERO, true))
        assertEquals(component, masker.mask(component, identities, CoordinateOffset.ZERO, false))
        assertEquals(component, masker.mask(expected, identities, CoordinateOffset.ZERO, false))
    }

    @Test
    fun `revealing names still shifts coordinates`() {
        val coordinates = ComponentMasker(TextMasker(listOf(Regex("X: (?<x>-?\\d+)"))))
        assertEquals(
            Component.text("${identity.alias} X: 116"),
            coordinates.mask(Component.text("${identity.alias} X: 100"), listOf(identity), CoordinateOffset(16, -16), true),
        )
    }

    @Test
    fun `checks the receiving permission instead of the target permission`() {
        assertEquals("", RevealedNames.placeholder(null, identity))
        assertEquals("", RevealedNames.placeholder(player(true, false), identity))
        val placeholder = RevealedNames.placeholder(player(false), identity)
        assertNotEquals(identity.realName, placeholder)
        assertEquals(identity.realName, RevealedNames.resolve(placeholder, true))
        assertEquals("", RevealedNames.resolve(placeholder, false))
    }

    @Test
    fun `reveals only the explicit placeholder to permitted recipients`() {
        val placeholder = RevealedNames.placeholder(player(true), identity)
        val component = Component.text("ExamplePlayer / $placeholder")
        assertEquals(Component.text("${identity.alias} / ${identity.realName}"), masker.mask(component, listOf(identity), CoordinateOffset.ZERO, true))
        assertEquals(Component.text("${identity.alias} / "), masker.mask(component, listOf(identity), CoordinateOffset.ZERO, false))
    }

    @Test
    fun `player name and real name placeholders produce alias followed by permitted real name`() {
        val placeholder = RevealedNames.placeholder(player(false), identity)
        for (name in listOf(identity.realName, identity.alias)) {
            val component = Component.text("$name  $placeholder", NamedTextColor.GREEN)
            assertEquals(
                Component.text("${identity.alias}  ${identity.realName}", NamedTextColor.GREEN),
                masker.mask(component, listOf(identity), CoordinateOffset.ZERO, true),
            )
            assertEquals(
                Component.text("${identity.alias}  ", NamedTextColor.GREEN),
                masker.mask(component, listOf(identity), CoordinateOffset.ZERO, false),
            )
        }
    }

    @Test
    fun `resolves placeholders without active identities and in hover text`() {
        val placeholder = RevealedNames.placeholder(player(true), identity)
        val component = Component.text("Name").hoverEvent(HoverEvent.showText(Component.text(placeholder)))
        assertEquals(Component.text("Name").hoverEvent(HoverEvent.showText(Component.text(identity.realName))), masker.mask(component, emptyList(), CoordinateOffset.ZERO, true))
        assertEquals(Component.text("Name").hoverEvent(HoverEvent.showText(Component.empty())), masker.mask(component, emptyList(), CoordinateOffset.ZERO, false))
    }

    @Test
    fun `handles placeholders split between text components`() {
        val placeholder = RevealedNames.placeholder(player(true), identity)
        val component = Component.text(placeholder.take(20)).append(Component.text(placeholder.drop(20)))
        assertEquals(Component.text(identity.realName), masker.mask(component, listOf(identity), CoordinateOffset.ZERO, true))
        assertEquals(Component.empty(), masker.mask(component, listOf(identity), CoordinateOffset.ZERO, false))
    }

    @Test
    fun `rejects unregistered tokens`() {
        assertEquals("", RevealedNames.resolve("__incognito_realname_00000000000000000000000000000000__", true))
    }

    private fun player(allowed: Boolean, online: Boolean = true): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader, arrayOf(Player::class.java),
    ) { proxy, method, _ ->
        when (method.name) {
            "getPlayer" -> if (online) proxy else null
            "getName" -> identity.realName
            "hasPermission" -> allowed
            else -> error(method.name)
        }
    } as Player
}
