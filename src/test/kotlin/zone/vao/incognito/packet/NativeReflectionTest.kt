package zone.vao.incognito.packet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class NativeReflectionTest {

    @JvmRecord
    private data class Entry(val name: String, val latency: Int, val listed: Boolean, val session: String?)

    @Test
    fun `reconstructs records without modifying original or unrelated fields`() {
        val original = Entry("ExamplePlayer", 42, true, null)
        val masked = NativeReflection.record(original) { name, value -> if (name == "name") "Anon_0123456789" else value }
        assertNotSame(original, masked)
        assertEquals(Entry("Anon_0123456789", 42, true, null), masked)
        assertEquals("ExamplePlayer", original.name)
    }
}
