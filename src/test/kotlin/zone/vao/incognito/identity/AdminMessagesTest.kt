package zone.vao.incognito.identity

import net.kyori.adventure.text.Component
import org.bukkit.configuration.file.YamlConfiguration
import zone.vao.incognito.config.Messages
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.packet.ComponentMasker
import zone.vao.incognito.packet.TextMasker
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminMessagesTest {
    private val recipient = UUID.randomUUID()
    private val identity = Identity(UUID.randomUUID(), "Notch", "incognito123")
    private val messages = Messages(YamlConfiguration())
    private val masker = ComponentMasker(TextMasker(emptyList()))

    @Test
    fun `administrative notifications preserve both names and formatting without reveal permission`() {
        for (key in listOf("notify-enabled", "notify-disabled", "notify-alias-changed", "notify-pending-disabled")) {
            val registry = AdminMessages()
            val message = messages.get(key, identity.alias, identity.realName)
            val token = registry.prepare(recipient, message)
            assertEquals(message, registry.resolve(token, recipient, true))
            assertEquals(Component.text(identity.alias), masker.system(Component.text(identity.realName), listOf(identity), CoordinateOffset.ZERO))
            assertNull(registry.resolve(message, recipient, true))
        }
    }

    @Test
    fun `tokens cannot be read by a different recipient or replayed`() {
        val registry = AdminMessages()
        val message = messages.get("notify-disabled", identity.alias, identity.realName)
        val token = registry.prepare(recipient, message)
        assertEquals(Component.empty(), registry.resolve(token, UUID.randomUUID(), true))
        assertEquals(message, registry.resolve(token, recipient, true))
        assertEquals(Component.empty(), registry.resolve(token, recipient, true))
    }

    @Test
    fun `revoking admin permission before sending suppresses the notification`() {
        val registry = AdminMessages()
        val token = registry.prepare(recipient, Component.text(identity.realName))
        assertEquals(Component.empty(), registry.resolve(token, recipient, false))
        assertEquals(Component.empty(), registry.resolve(token, recipient, true))
    }

    @Test
    fun `expired and unknown tokens are suppressed`() {
        var now = 0L
        val registry = AdminMessages { now }
        val token = registry.prepare(recipient, Component.text(identity.realName))
        now = TimeUnit.MINUTES.toNanos(1)
        assertEquals(Component.empty(), registry.resolve(token, recipient, true))
        assertEquals(Component.empty(), registry.resolve(Component.text("__incognito_admin_${"0".repeat(32)}__"), recipient, true))
    }
}
