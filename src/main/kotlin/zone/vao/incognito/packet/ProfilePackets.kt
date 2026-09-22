package zone.vao.incognito.packet

import zone.vao.incognito.identity.Identity
import java.util.EnumSet
import java.util.UUID

internal object ProfilePackets {

    fun update(packet: Any, identities: List<Identity>, profiles: ProfileIds, transform: (UUID, UUID, String, Any?) -> Any?): Any? {
        val fields = NativeReflection.fields(packet.javaClass)
        val actions = fields.single { EnumSet::class.java.isAssignableFrom(it.type) }.get(packet) as EnumSet<*>
        val entries = fields.single { List::class.java.isAssignableFrom(it.type) }.get(packet) as List<*>
        val adding = actions.any { (it as Enum<*>).name == "ADD_PLAYER" }
        val masked = entries.mapNotNull { entry ->
            requireNotNull(entry)
            val id = entry.javaClass.getMethod("profileId").invoke(entry) as UUID
            val target = profiles.profile(id, identities, adding) ?: return@mapNotNull null
            NativeReflection.record(entry) { name, value ->
                if (name == "profileId") target else transform(id, target, name, value)
            }
        }
        return if (masked.isEmpty()) null else packet.javaClass.getConstructor(EnumSet::class.java, List::class.java).newInstance(actions, masked)
    }
}
