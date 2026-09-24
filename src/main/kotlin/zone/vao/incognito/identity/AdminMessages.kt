package zone.vao.incognito.identity

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class AdminMessages(private val clock: () -> Long = System::nanoTime) {
    private data class Entry(val recipient: UUID, val message: Component, val expires: Long)
    private val pending = LinkedHashMap<String, Entry>()
    private val pattern = Regex("__incognito_admin_[a-f0-9]{32}__")

    @Synchronized
    fun prepare(recipient: UUID, message: Component): Component {
        val now = clock()
        pending.entries.removeIf { now - it.value.expires >= 0 }
        while (pending.size >= 4096) pending.remove(pending.keys.first())
        val token = "__incognito_admin_${UUID.randomUUID().toString().replace("-", "")}__"
        pending[token] = Entry(recipient, message, now + TimeUnit.MINUTES.toNanos(1))
        return Component.text(token)
    }

    @Synchronized
    fun resolve(component: Component, recipient: UUID?, allowed: Boolean): Component? {
        val token = (component as? TextComponent)?.content() ?: return null
        if (!pattern.matches(token)) return null
        val entry = pending[token] ?: return Component.empty()
        if (entry.recipient != recipient) return Component.empty()
        pending.remove(token)
        return if (allowed && clock() - entry.expires < 0) entry.message else Component.empty()
    }
}
