package zone.vao.incognito.packet

import zone.vao.incognito.identity.Identity
import java.util.UUID

internal class ProfileIds(private val viewer: UUID?) {

    private val sent = HashMap<UUID, UUID>()

    fun outbound(id: UUID, identities: List<Identity>): UUID =
        if (id == viewer) id else identities.firstOrNull { it.id == id }?.maskedId ?: id

    fun profile(id: UUID, identities: List<Identity>, adding: Boolean = true): UUID =
        if (adding) outbound(id, identities).also { sent[id] = it } else sent[id] ?: outbound(id, identities)

    fun remove(id: UUID, identities: List<Identity>): UUID = sent.remove(id) ?: outbound(id, identities)

    fun inbound(id: UUID, identities: List<Identity>): UUID =
        identities.firstOrNull { it.id != viewer && it.maskedId == id }?.id ?: id
}
