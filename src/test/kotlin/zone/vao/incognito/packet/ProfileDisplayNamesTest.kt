package zone.vao.incognito.packet

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.identity.Identity
import java.util.EnumSet
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileDisplayNamesTest {

    private val identity = Identity(UUID.randomUUID(), "ExamplePlayer", "Anon_0123456789")
    private val profiles = ProfileIds(UUID.randomUUID())
    private val masker = ComponentMasker(TextMasker(emptyList()))
    private val initial = EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_DISPLAY_NAME)
    private val update = EnumSet.of(Action.UPDATE_DISPLAY_NAME)

    @Test
    fun `initial display name is sent and duplicates are compared after masking`() {
        val source = Component.text(identity.realName, NamedTextColor.GREEN)
        val alias = Component.text(identity.alias, NamedTextColor.GREEN)
        assertEquals(alias, send(initial, source)!!.entries.single().displayName)
        assertNull(send(update, source))
        assertNull(send(update, alias))
        assertEquals(initial, send(initial, alias)!!.actions)
    }

    @Test
    fun `formatting changes and clearing the display name are preserved`() {
        send(initial, Component.text(identity.realName, NamedTextColor.GREEN))
        val changed = Component.text("[VIP] ", NamedTextColor.GOLD).append(Component.text(identity.realName, NamedTextColor.RED))
        val expected = Component.text("[VIP] ", NamedTextColor.GOLD).append(Component.text(identity.alias, NamedTextColor.RED))
        assertEquals(expected, send(update, changed)!!.entries.single().displayName)
        assertNull(send(update, changed))
        assertNull(send(update, null)!!.entries.single().displayName)
        assertNull(send(update, null))
    }

    @Test
    fun `other actions survive an unchanged display name without mutating the original packet`() {
        send(initial, Component.text(identity.realName))
        val actions = EnumSet.of(Action.UPDATE_DISPLAY_NAME, Action.UPDATE_LATENCY)
        val packet = Packet(actions, listOf(Entry(identity.id, Component.text(identity.realName), 120)))
        val result = transform(packet)!!
        assertEquals(EnumSet.of(Action.UPDATE_LATENCY), result.actions)
        assertEquals(120, result.entries.single().latency)
        assertEquals(EnumSet.of(Action.UPDATE_DISPLAY_NAME, Action.UPDATE_LATENCY), packet.actions)
    }

    @Test
    fun `mixed player updates retain changes and remove only duplicate display entries`() {
        val other = UUID.randomUUID()
        transform(Packet(initial, listOf(Entry(identity.id, Component.text(identity.realName)), Entry(other, Component.text("OtherPlayer")))))
        val packet = Packet(update, listOf(Entry(identity.id, Component.text(identity.realName)), Entry(other, Component.text("[VIP] OtherPlayer"))))
        assertEquals(listOf(packet.entries.last()), transform(packet)!!.entries)
        val combined = Packet(EnumSet.of(Action.UPDATE_DISPLAY_NAME, Action.UPDATE_LATENCY), listOf(
            Entry(identity.id, Component.text(identity.realName), 50), Entry(other, Component.text("[Admin] OtherPlayer"), 70),
        ))
        assertEquals(combined.actions, transform(combined)!!.actions)
        assertEquals(listOf(50, 70), transform(combined)!!.entries.map { it.latency })
    }

    @Test
    fun `each viewer has independent state and removal resets cached display names`() {
        val name = Component.text(identity.realName)
        send(initial, name)
        val viewer = ProfileIds(UUID.randomUUID())
        transform(Packet(EnumSet.of(Action.ADD_PLAYER), listOf(Entry(identity.id, null))), viewer)
        assertEquals(Component.text(identity.alias), transform(Packet(update, listOf(Entry(identity.id, name))), viewer)!!.entries.single().displayName)
        assertNull(send(update, name))
        profiles.remove(identity.id, listOf(identity))
        assertEquals(true, profiles.displayName(identity.maskedId, Component.text(identity.alias)))
        send(EnumSet.of(Action.ADD_PLAYER), null)
        assertEquals(Component.text(identity.alias), send(update, name)!!.entries.single().displayName)
    }

    private fun send(actions: EnumSet<Action>, name: Component?): Packet? =
        transform(Packet(actions, listOf(Entry(identity.id, name))))

    private fun transform(packet: Packet, viewer: ProfileIds = profiles): Packet? =
        ProfilePackets.update(packet, listOf(identity), viewer) { _, _, name, value ->
            if (name == "displayName" && value is Component) masker.mask(value, listOf(identity), CoordinateOffset.ZERO) else value
        } as Packet?

    enum class Action { ADD_PLAYER, UPDATE_DISPLAY_NAME, UPDATE_LATENCY }

    @JvmRecord
    data class Entry(val profileId: UUID, val displayName: Component?, val latency: Int = 0)

    class Packet(val actions: EnumSet<Action>, val entries: List<Entry>)
}
