package zone.vao.incognito.packet

import zone.vao.incognito.identity.Identity
import java.util.EnumSet
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ProfilePacketsTest {

    private val player = UUID.randomUUID()
    private val identity = Identity(player, "ExamplePlayer", "Anon_0123456789")
    private val profiles = ProfileIds(UUID.randomUUID())

    @Test
    fun `enabling cannot attach an alias to the previously visible uuid`() {
        assertEquals(Entry(player, "ExamplePlayer"), send(Action.ADD_PLAYER, emptyList())!!.entries.single())
        assertNull(send(Action.UPDATE_DISPLAY_NAME, listOf(identity)))
        assertNull(send(Action.ADD_PLAYER, listOf(identity)))
        assertEquals(player, profiles.remove(player, listOf(identity)))
        assertNull(send(Action.UPDATE_DISPLAY_NAME, listOf(identity)))
        assertEquals(Entry(identity.maskedId, identity.alias), send(Action.ADD_PLAYER, listOf(identity))!!.entries.single())
        assertEquals(Entry(identity.maskedId, identity.alias), send(Action.UPDATE_DISPLAY_NAME, listOf(identity))!!.entries.single())
    }

    @Test
    fun `disabling cannot attach a real name to the previous alias uuid`() {
        send(Action.ADD_PLAYER, listOf(identity))
        assertNull(send(Action.UPDATE_DISPLAY_NAME, emptyList()))
        assertNull(send(Action.ADD_PLAYER, emptyList()))
        assertEquals(identity.maskedId, profiles.remove(player, emptyList()))
        assertNull(send(Action.UPDATE_DISPLAY_NAME, emptyList()))
        assertEquals(Entry(player, "ExamplePlayer"), send(Action.ADD_PLAYER, emptyList())!!.entries.single())
    }

    @Test
    fun `joining incognito drops early updates and introduces only the masked profile`() {
        assertNull(send(Action.UPDATE_DISPLAY_NAME, listOf(identity)))
        assertEquals(Entry(identity.maskedId, identity.alias), send(Action.ADD_PLAYER, listOf(identity))!!.entries.single())
    }

    @Test
    fun `reconnecting does not attach the next alias to the previous session uuid`() {
        send(Action.ADD_PLAYER, listOf(identity))
        val next = Identity(player, "ExamplePlayer", "Anon_9876543210")
        assertNotEquals(identity.maskedId, next.maskedId)
        assertNull(send(Action.UPDATE_DISPLAY_NAME, listOf(next)))
        assertEquals(identity.maskedId, profiles.remove(player, listOf(next)))
        assertNull(send(Action.UPDATE_DISPLAY_NAME, listOf(next)))
        assertEquals(Entry(next.maskedId, next.alias), send(Action.ADD_PLAYER, listOf(next))!!.entries.single())
    }

    @Test
    fun `filtering a transition preserves updates for other players in the same packet`() {
        val other = UUID.randomUUID()
        transform(Packet(EnumSet.of(Action.ADD_PLAYER), listOf(Entry(player, "ExamplePlayer"), Entry(other, "OtherPlayer"))), emptyList())
        val packet = Packet(EnumSet.of(Action.UPDATE_DISPLAY_NAME), listOf(Entry(player, "ExamplePlayer"), Entry(other, "OtherPlayer")))
        assertEquals(listOf(Entry(other, "OtherPlayer")), transform(packet, listOf(identity))!!.entries)
        assertEquals(Entry(player, "ExamplePlayer"), packet.entries.first())
    }

    private fun send(action: Action, identities: List<Identity>): Packet? =
        transform(Packet(EnumSet.of(action), listOf(Entry(player, "ExamplePlayer"))), identities)

    private fun transform(packet: Packet, identities: List<Identity>): Packet? =
        ProfilePackets.update(packet, identities, profiles) { id, _, name, value ->
            if (name == "displayName") identities.firstOrNull { it.id == id }?.alias ?: value else value
        } as Packet?

    enum class Action { ADD_PLAYER, UPDATE_DISPLAY_NAME }

    @JvmRecord
    data class Entry(val profileId: UUID, val displayName: String)

    class Packet(val actions: EnumSet<Action>, val entries: List<Entry>)
}
