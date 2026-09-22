package zone.vao.incognito.packet

import zone.vao.incognito.identity.Identity
import java.util.UUID

internal class ProfileIds(private val viewer: UUID?) {

    private val sent = HashMap<UUID, UUID>()
    private val displayNames = HashMap<UUID, Any?>()

    fun outbound(id: UUID, identities: List<Identity>): UUID =
        if (id == viewer) id else identities.firstOrNull { it.id == id }?.maskedId ?: id

    fun profile(id: UUID, identities: List<Identity>, adding: Boolean = true): UUID? {
        val target = outbound(id, identities)
        val previous = sent[id]
        if (previous != null && previous != target || !adding && previous == null) return null
        if (adding) sent[id] = target
        return target
    }

    fun displayName(id: UUID, value: Any?): Boolean {
        val changed = !displayNames.containsKey(id) || displayNames[id] != value
        displayNames[id] = value
        return changed
    }

    fun remove(id: UUID, identities: List<Identity>): UUID =
        (sent.remove(id) ?: outbound(id, identities)).also { displayNames.remove(it) }

    fun inbound(id: UUID, identities: List<Identity>): UUID =
        identities.firstOrNull { it.id != viewer && it.maskedId == id }?.id ?: id
}
