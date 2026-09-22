package zone.vao.incognito.identity

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IdentitySessionsTest {

    private var now = 0L
    private val sessions = IdentitySessions(retention = 30, capacity = 2, clock = { now })
    private val identity = Identity(UUID.randomUUID(), "ExamplePlayer", "Anon_0123456789")

    @Test
    fun `packet snapshots are reused and departed players leave active lookups immediately`() {
        sessions.put(identity)
        assertSame(sessions.active(), sessions.active())
        assertSame(sessions.packets(), sessions.packets())
        val before = sessions.active()
        sessions.retire(identity.id)
        assertTrue(sessions.active().isEmpty())
        assertEquals(listOf(identity), before)
        assertEquals(listOf(identity), sessions.packets())
        assertEquals(identity, sessions.previous(identity.id))
        now = 30
        assertTrue(sessions.packets().isEmpty())
        assertNull(sessions.previous(identity.id))
    }

    @Test
    fun `reconnecting replaces old identity and expiration cannot remove the new session`() {
        sessions.put(identity)
        sessions.retire(identity.id)
        val next = Identity(identity.id, identity.realName, "Anon_9876543210")
        sessions.put(next)
        sessions.retire(identity.id, identity)
        now = 100
        assertEquals(listOf(next), sessions.active())
        assertEquals(listOf(next), sessions.packets())
        sessions.remove(identity.id)
        assertTrue(sessions.packets().isEmpty())
    }

    @Test
    fun `departure history has a fixed bound without evicting active players`() {
        sessions.put(identity)
        val departed = (1..3).map { Identity(UUID.randomUUID(), "Player$it", "Anon_$it") }
        departed.forEach {
            sessions.put(it)
            sessions.retire(it.id)
        }
        assertEquals(listOf(identity), sessions.active())
        assertEquals(listOf(identity) + departed.takeLast(2), sessions.packets())
        assertNull(sessions.previous(departed.first().id))
    }
}
