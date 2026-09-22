package zone.vao.incognito.identity

import java.util.UUID
import java.util.concurrent.TimeUnit

internal class IdentitySessions(
    private val retention: Long = TimeUnit.SECONDS.toNanos(30),
    private val capacity: Int = 256,
    private val clock: () -> Long = System::nanoTime,
) {

    private val current = LinkedHashMap<UUID, Identity>()
    private val recent = LinkedHashMap<UUID, Pair<Identity, Long>>()
    @Volatile private var active = emptyList<Identity>()
    @Volatile private var packets = emptyList<Identity>()
    @Volatile private var expires: Long? = null

    fun active(): List<Identity> = active

    fun packets(): List<Identity> {
        expires?.let { if (clock() - it >= 0) expire() }
        return packets
    }

    @Synchronized
    fun previous(id: UUID): Identity? {
        expire()
        return current[id] ?: recent[id]?.first
    }

    @Synchronized
    fun put(identity: Identity) {
        recent.remove(identity.id)
        current[identity.id] = identity
        publish()
    }

    @Synchronized
    fun remove(id: UUID) {
        val activeRemoved = current.remove(id)
        val recentRemoved = recent.remove(id)
        if (activeRemoved != null || recentRemoved != null) publish()
    }

    @Synchronized
    fun retire(id: UUID, expected: Identity? = null) {
        val identity = current[id] ?: return
        if (expected != null && identity != expected) return
        current.remove(id)
        recent.remove(id)
        recent[id] = identity to (clock() + retention)
        while (recent.size > capacity) recent.remove(recent.keys.first())
        publish()
    }

    @Synchronized
    private fun expire() {
        val now = clock()
        if (recent.entries.removeIf { now - it.value.second >= 0 }) publish()
    }

    private fun publish() {
        active = current.values.toList()
        packets = active + recent.values.map { it.first }
        expires = recent.values.firstOrNull()?.second
    }
}
