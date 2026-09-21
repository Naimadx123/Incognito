package zone.vao.incognito.identity

import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class HeadProfilesTest {

    private val identity = Identity(UUID.randomUUID(), "ExamplePlayer", "Anon_0123456789")
    private val identities = listOf(identity)

    @Test
    fun `head owners are found by profile uuid name or embedded texture identity`() {
        assertEquals(identity, HeadProfiles.find(identity.id, null, emptyList(), identities))
        assertEquals(identity, HeadProfiles.find(UUID.randomUUID(), "exampleplayer", emptyList(), identities))
        val texture = encode("""{"profileId":"${identity.id.toString().replace("-", "")}","profileName":"ExamplePlayer","textures":{}}""")
        assertEquals(identity, HeadProfiles.find(UUID.randomUUID(), null, listOf(texture), identities))
        assertEquals(identity, HeadProfiles.find(null, null, listOf(encode("""{"profileName":"ExamplePlayer"}""")), identities))
    }

    @Test
    fun `unrelated and malformed texture profiles remain unassociated`() {
        assertNull(HeadProfiles.find(UUID.randomUUID(), "OtherPlayer", listOf("invalid", encode("{}")), identities))
        assertNull(HeadProfiles.find(null, null, listOf(encode("""{"textures":{"SKIN":{"url":"https://example.invalid/skin"}}}""")), identities))
    }

    @Test
    fun `hidden skin uses a separate stable profile uuid for each session alias`() {
        assertNotEquals(identity.id, HeadProfiles.maskedId(identity))
        assertEquals(HeadProfiles.maskedId(identity), HeadProfiles.maskedId(identity))
        assertNotEquals(HeadProfiles.maskedId(identity), HeadProfiles.maskedId(identity.copy(alias = "Anon_9876543210")))
    }

    private fun encode(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
}
