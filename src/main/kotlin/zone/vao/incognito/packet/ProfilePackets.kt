package zone.vao.incognito.packet

import zone.vao.incognito.identity.Identity
import java.util.EnumSet
import java.util.UUID

internal object ProfilePackets {

    fun update(packet: Any, identities: List<Identity>, profiles: ProfileIds, displayKey: (Any?) -> Any? = { it }, transform: (UUID, UUID, String, Any?) -> Any?): Any? {
        val fields = NativeReflection.fields(packet.javaClass)
        val actions = fields.single { EnumSet::class.java.isAssignableFrom(it.type) }.get(packet) as EnumSet<*>
        val entries = fields.single { List::class.java.isAssignableFrom(it.type) }.get(packet) as List<*>
        val adding = actions.any { (it as Enum<*>).name == "ADD_PLAYER" }
        val display = actions.firstOrNull { (it as Enum<*>).name == "UPDATE_DISPLAY_NAME" }
        var displayChanged = false
        val masked = entries.mapNotNull { entry ->
            requireNotNull(entry)
            val id = entry.javaClass.getMethod("profileId").invoke(entry) as UUID
            val target = profiles.profile(id, identities, adding) ?: return@mapNotNull null
            val result = NativeReflection.record(entry) { name, value ->
                if (name == "profileId") target else transform(id, target, name, value)
            }
            if (display != null) {
                val value = result.javaClass.getMethod("displayName").invoke(result)
                val changed = profiles.displayName(target, displayKey(value))
                displayChanged = displayChanged || changed
                if (!changed && !adding && actions.size == 1) return@mapNotNull null
            } else if (adding) profiles.displayName(target, null)
            result
        }
        if (masked.isEmpty()) return null
        val remaining = actions.clone()
        if (!adding && display != null && !displayChanged) remaining.remove(display)
        return if (remaining.isEmpty()) null else packet.javaClass.getConstructor(EnumSet::class.java, List::class.java).newInstance(remaining, masked)
    }
}
