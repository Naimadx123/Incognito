package zone.vao.incognito.packet

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.identity.Identity
import zone.vao.incognito.command.CommandNames
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ComponentMaskerTest {

    private val identity = Identity(UUID.randomUUID(), "ExamplePlayer", "Anon_0123456789")
    private val offset = CoordinateOffset(16, -16)
    private val masker = ComponentMasker(TextMasker(listOf(Regex("X: (?<x>-?\\d+)"))))

    @Test
    fun `plugin errors display aliases instead of internal lookup identifiers`() {
        val token = CommandNames.rewrite("/alts ${identity.realName}", listOf(identity)) { true }.substringAfter(' ')
        val message = Component.text("Error: Player $token was never on this server.", NamedTextColor.RED)
            .hoverEvent(HoverEvent.showText(Component.text(token)))
        val expected = Component.text("Error: Player ${identity.alias} was never on this server.", NamedTextColor.RED)
            .hoverEvent(HoverEvent.showText(Component.text(identity.alias)))
        assertEquals(expected, masker.system(message, listOf(identity), CoordinateOffset.ZERO, true))
        assertEquals(expected, masker.system(message, emptyList(), CoordinateOffset.ZERO))
        val split = Component.text("Error: Player ${token.take(20)}").append(Component.text("${token.drop(20)} was never on this server."))
        assertEquals(Component.text("Error: Player ${identity.alias} was never on this server."), masker.system(split, emptyList(), CoordinateOffset.ZERO))
    }

    @Test
    fun `system player lists mask names and interactive metadata even for reveal viewers`() {
        val player = Component.text(identity.realName, NamedTextColor.GOLD)
            .clickEvent(ClickEvent.suggestCommand("/msg ${identity.realName} "))
            .hoverEvent(HoverEvent.showText(Component.text(identity.realName)))
        val alias = Component.text(identity.alias, NamedTextColor.GOLD)
            .clickEvent(ClickEvent.suggestCommand("/msg ${identity.alias} "))
            .hoverEvent(HoverEvent.showText(Component.text(identity.alias)))
        val message = Component.text("Online: ").append(player).append(Component.text(", OtherPlayer"))
        val expected = Component.text("Online: ").append(alias).append(Component.text(", OtherPlayer"))
        for (reveal in listOf(false, true)) {
            assertEquals(expected, masker.system(message, listOf(identity), CoordinateOffset.ZERO, reveal))
        }
        assertEquals(Component.text("Online: ${identity.alias}"), masker.system(Component.text("Online: Example").append(Component.text("Player")), listOf(identity), CoordinateOffset.ZERO))
    }

    @Test
    fun `chat keeps real name mentions and masks only the structured sender`() {
        val notch = Identity(UUID.randomUUID(), "Notch", "Anon_abcdef1234")
        val message = Component.text("hey Notch", NamedTextColor.GREEN)
        assertEquals(message, masker.chat(message, listOf(notch), CoordinateOffset.ZERO))
        val formatted = Component.translatable("chat.type.text", Component.text("Notch"), message)
        val expected = Component.translatable("chat.type.text", Component.text(notch.alias), message)
        assertEquals(expected, masker.chat(formatted, listOf(notch), CoordinateOffset.ZERO))
        assertEquals(expected, masker.system(formatted, listOf(notch), CoordinateOffset.ZERO))
        val privateMessage = Component.translatable("commands.message.display.incoming", Component.text("Notch"), message)
        assertEquals(Component.translatable("commands.message.display.incoming", Component.text(notch.alias), message), masker.chat(privateMessage, listOf(notch), CoordinateOffset.ZERO))
    }

    @Test
    fun `masks names and coordinates split across components`() {
        val component = Component.text("Example").append(Component.text("Player X: ")).append(Component.text("-123"))
        assertEquals(Component.text("Anon_0123456789 X: -107"), masker.mask(component, listOf(identity), offset))
    }

    @Test
    fun `masks hidden identity and coordinate metadata in place`() {
        val component = Component.text("Click")
            .clickEvent(ClickEvent.runCommand("/tp ExamplePlayer"))
            .hoverEvent(HoverEvent.showText(Component.text("X: 123")))
        val expected = Component.text("Click")
            .clickEvent(ClickEvent.runCommand("/tp Anon_0123456789"))
            .hoverEvent(HoverEvent.showText(Component.text("X: 139")))
        assertEquals(expected, masker.mask(component, listOf(identity), offset))
    }

    @Test
    fun `keeps styles and translation arguments when names sit in whole nodes`() {
        val component = Component.translatable("multiplayer.player.joined", NamedTextColor.YELLOW, Component.text("ExamplePlayer", NamedTextColor.GOLD))
        val expected = Component.translatable("multiplayer.player.joined", NamedTextColor.YELLOW, Component.text("Anon_0123456789", NamedTextColor.GOLD))
        assertEquals(expected, masker.mask(component, listOf(identity), offset))
        val entity = Component.text("ExamplePlayer").hoverEvent(HoverEvent.showEntity(Key.key("minecraft:player"), identity.id, Component.text("ExamplePlayer")))
            .clickEvent(ClickEvent.suggestCommand("/tell ExamplePlayer ")).insertion("ExamplePlayer")
        val maskedEntity = Component.text("Anon_0123456789").hoverEvent(HoverEvent.showEntity(Key.key("minecraft:player"), identity.id, Component.text("Anon_0123456789")))
            .clickEvent(ClickEvent.suggestCommand("/tell Anon_0123456789 ")).insertion("Anon_0123456789")
        assertEquals(maskedEntity, masker.mask(entity, listOf(identity), offset))
        val styled = Component.text("ExamplePlayer", NamedTextColor.RED).append(Component.text(" X: 1", NamedTextColor.GREEN))
        assertEquals(Component.text("Anon_0123456789", NamedTextColor.RED).append(Component.text(" X: 17", NamedTextColor.GREEN)), masker.mask(styled, listOf(identity), offset))
    }

    @Test
    fun `preserves formatting and actions for unaffected messages`() {
        val component = Component.text("Welcome", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/help"))
        assertSame(component, masker.mask(component, listOf(identity), offset))
    }
}
