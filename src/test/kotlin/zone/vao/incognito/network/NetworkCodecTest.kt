package zone.vao.incognito.network

import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.storage.PlayerRecord
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkCodecTest {

    @Test
    fun `sessions survive a round trip`() {
        val session = NetworkSession("Anon_0123456789", UUID.randomUUID(), CoordinateOffset(-32_768, 65_536))
        assertEquals(session, NetworkCodec.session(NetworkCodec.session(session)))
        assertNull(NetworkCodec.session("broken"))
    }

    @Test
    fun `records keep nullable fields`() {
        val id = UUID.randomUUID()
        listOf(
            PlayerRecord(id, "Notch", true, null, null),
            PlayerRecord(id, "Naimad123", false, true, "Anon_0123456789"),
            PlayerRecord(id, "Notch", true, false, null),
        ).forEach { record ->
            assertEquals("server" to record, NetworkCodec.record(NetworkCodec.record("server", record)))
        }
        assertNull(NetworkCodec.record("server|not-a-uuid|Notch|1||"))
    }

    @Test
    fun `random offsets stay chunk aligned and far from the origin`() {
        repeat(100) {
            val offset = CoordinateOffset.random()
            listOf(offset.x, offset.z).forEach { axis ->
                assertEquals(0, axis % 16)
                check(kotlin.math.abs(axis) in 32_768 until 65_536)
            }
        }
    }
}
