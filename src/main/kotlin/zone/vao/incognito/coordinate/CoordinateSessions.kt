package zone.vao.incognito.coordinate

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CoordinateSessions {

    private val offsets = ConcurrentHashMap<UUID, CoordinateOffset>()

    fun get(id: UUID): CoordinateOffset = offsets[id] ?: CoordinateOffset.ZERO

    fun enable(id: UUID, offset: CoordinateOffset? = null) {
        if (offset != null) offsets[id] = offset else offsets.computeIfAbsent(id) { CoordinateOffset.random() }
    }

    fun disable(id: UUID) {
        offsets.remove(id)
    }
}
