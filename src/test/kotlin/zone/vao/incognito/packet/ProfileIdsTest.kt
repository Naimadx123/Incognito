package zone.vao.incognito.packet

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.identity.Identity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ProfileIdsTest {

    private val identity = Identity(UUID.randomUUID(), "ExamplePlayer", "Anon_0123456789")
    private val identities = listOf(identity)
    private val profiles = ProfileIds(UUID.randomUUID())

    @Test
    fun `profile and entity identifiers agree and spectator targets are restored`() {
        val sent = profiles.profile(identity.id, identities)
        assertNotEquals(identity.id, sent)
        assertEquals(sent, profiles.outbound(identity.id, identities))
        assertEquals(identity.id, profiles.inbound(requireNotNull(sent), identities))
        val unrelated = UUID.randomUUID()
        assertEquals(unrelated, profiles.outbound(unrelated, identities))
        assertEquals(unrelated, profiles.inbound(unrelated, identities))
    }

    @Test
    fun `removal uses the previously sent uuid across enabling and disabling`() {
        assertEquals(identity.id, profiles.profile(identity.id, emptyList()))
        assertNull(profiles.profile(identity.id, identities, false))
        assertEquals(identity.id, profiles.remove(identity.id, identities))
        assertEquals(identity.maskedId, profiles.profile(identity.id, identities))
        assertNull(profiles.profile(identity.id, emptyList(), false))
        assertEquals(identity.maskedId, profiles.remove(identity.id, emptyList()))
        assertEquals(identity.id, profiles.profile(identity.id, emptyList()))
    }

    @Test
    fun `own profile stays unchanged and new sessions receive new identifiers`() {
        assertEquals(identity.id, ProfileIds(identity.id).profile(identity.id, identities))
        val next = Identity(identity.id, identity.realName, identity.alias)
        assertNotEquals(identity.maskedId, next.maskedId)
        assertEquals(identity.maskedId, identity.copy(realName = "DisplayName").maskedId)
    }

    @Test
    fun `entity hover does not expose uuid even without a name`() {
        val component = Component.text("Player").hoverEvent(HoverEvent.showEntity(Key.key("minecraft:player"), identity.id))
        val masked = ComponentMasker(TextMasker(emptyList())).mask(component, identities, CoordinateOffset.ZERO)
        assertEquals(identity.maskedId, (masked.hoverEvent()!!.value() as HoverEvent.ShowEntity).id())
    }
}
