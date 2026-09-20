package zone.vao.incognito.coordinate

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CoordinateSessions(private val file: File) {

    private val offsets = ConcurrentHashMap<UUID, CoordinateOffset>()
    private val random = SecureRandom()

    init {
        if (file.exists()) {
            val data = YamlConfiguration().apply { load(file) }
            data.getKeys(false).forEach { key ->
                offsets[UUID.fromString(key)] = CoordinateOffset(data.getInt("$key.x"), data.getInt("$key.z"))
            }
        }
    }

    fun get(id: UUID): CoordinateOffset = offsets[id] ?: CoordinateOffset.ZERO

    @Synchronized
    fun enable(id: UUID): Boolean {
        if (offsets.containsKey(id)) return false
        fun axis(): Int = (2048 + random.nextInt(2048)) * 16 * if (random.nextBoolean()) 1 else -1
        val offset = CoordinateOffset(axis(), axis())
        save(offsets + (id to offset))
        offsets[id] = offset
        return true
    }

    @Synchronized
    fun disable(id: UUID): Boolean {
        if (!offsets.containsKey(id)) return false
        save(offsets - id)
        offsets.remove(id)
        return true
    }

    private fun save(values: Map<UUID, CoordinateOffset>) {
        val data = YamlConfiguration()
        values.forEach { (id, offset) ->
            data.set("$id.x", offset.x)
            data.set("$id.z", offset.z)
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        data.save(temporary)
        java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
