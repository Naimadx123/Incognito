package zone.vao.incognito.packet

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OutboundMaskHandlerTest {

    @Test
    fun `forwards a replacement without changing another recipient`() {
        val original = Any()
        val replacement = Any()
        val masked = EmbeddedChannel(OutboundMaskHandler({ replacement }, { _, error -> throw error }))
        val unchanged = EmbeddedChannel(OutboundMaskHandler({ it }, { _, error -> throw error }))
        try {
            assertTrue(masked.writeOutbound(original))
            assertTrue(unchanged.writeOutbound(original))
            assertSame(replacement, masked.readOutbound())
            assertSame(original, unchanged.readOutbound())
        } finally {
            masked.finishAndReleaseAll()
            unchanged.finishAndReleaseAll()
        }
    }

    @Test
    fun `suppressed packets release their buffer and complete their promise`() {
        val channel = EmbeddedChannel(OutboundMaskHandler({ null }, { _, error -> throw error }))
        try {
            val message = Unpooled.buffer().writeByte(1)
            val result = channel.writeAndFlush(message)
            assertTrue(result.isSuccess)
            assertEquals(0, message.refCnt())
            assertNull(channel.readOutbound<Any>())
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    @Test
    fun `filter failures cannot forward the original packet`() {
        var failure: Throwable? = null
        val channel = EmbeddedChannel(OutboundMaskHandler({ error("Unsupported packet") }, { _, error -> failure = error }))
        try {
            assertFalse(channel.writeOutbound(Any()))
            assertTrue(failure is IllegalStateException)
            assertNull(channel.readOutbound<Any>())
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    @Test
    fun `linkage errors fail closed`() {
        var reported = false
        val channel = EmbeddedChannel(OutboundMaskHandler({ throw NoSuchMethodError() }, { _, _ -> reported = true }))
        try {
            assertFalse(channel.writeOutbound(Any()))
            assertTrue(reported)
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    @Test
    fun `incoming packets are transformed before the server receives them`() {
        val original = Any()
        val translated = Any()
        val channel = EmbeddedChannel(OutboundMaskHandler({ it }, { _, error -> throw error }, { translated }))
        try {
            assertTrue(channel.writeInbound(original))
            assertSame(translated, channel.readInbound())
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    @Test
    fun `incoming translation failures close the connection without forwarding`() {
        val channel = EmbeddedChannel(OutboundMaskHandler({ it }, { _, _ -> }, { error("Unsupported movement") }))
        try {
            assertFalse(channel.writeInbound(Any()))
            assertFalse(channel.isOpen)
            assertNull(channel.readInbound<Any>())
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
