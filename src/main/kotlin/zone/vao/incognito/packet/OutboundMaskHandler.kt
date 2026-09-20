package zone.vao.incognito.packet

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelPromise
import io.netty.util.ReferenceCountUtil

class OutboundMaskHandler(
    private val transform: (Any) -> Any?,
    private val failure: (Any, Throwable) -> Unit,
    private val inbound: (Any) -> Any? = { it },
) : ChannelDuplexHandler() {

    override fun channelRead(context: ChannelHandlerContext, message: Any) {
        val translated = try {
            inbound(message)
        } catch (exception: Exception) {
            ReferenceCountUtil.release(message)
            failure(message, exception)
            context.close()
            return
        } catch (error: LinkageError) {
            ReferenceCountUtil.release(message)
            failure(message, error)
            context.close()
            return
        }
        if (translated !== message) ReferenceCountUtil.release(message)
        if (translated != null) context.fireChannelRead(translated)
    }

    override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
        val masked = try {
            transform(message)
        } catch (exception: Exception) {
            ReferenceCountUtil.release(message)
            promise.trySuccess()
            failure(message, exception)
            return
        } catch (error: LinkageError) {
            ReferenceCountUtil.release(message)
            promise.trySuccess()
            failure(message, error)
            return
        }
        if (masked !== message) ReferenceCountUtil.release(message)
        if (masked == null) promise.trySuccess() else context.write(masked, promise)
    }
}
