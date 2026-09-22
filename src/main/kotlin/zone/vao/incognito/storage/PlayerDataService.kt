package zone.vao.incognito.storage

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

class PlayerDataService(private val storage: PlayerStorage, private val logger: Logger) : AutoCloseable {

    private val cache = ConcurrentHashMap<UUID, PlayerRecord>()
    private val dirty = ConcurrentHashMap<UUID, PlayerRecord>()
    private val writes = Any()
    private val io = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "incognito-storage").apply { isDaemon = true } }
    @Volatile private var closed = false

    init {
        try {
            storage.loadAll().forEach { cache[it.id] = it }
            logger.info("Loaded ${cache.size} incognito records into memory.")
            io.scheduleWithFixedDelay({
                runCatching { flush() }.onFailure { logger.log(Level.SEVERE, "Cannot save incognito data; pending changes will be retried.", it) }
            }, 1, 1, TimeUnit.SECONDS)
        } catch (error: Exception) {
            io.shutdownNow()
            storage.close()
            throw error
        }
    }

    fun get(id: UUID): PlayerRecord? = cache[id]

    @Synchronized
    fun save(record: PlayerRecord) {
        check(!closed) { "Incognito storage is closed" }
        cache[record.id] = record
        dirty[record.id] = record
    }

    fun flush() = synchronized(writes) {
        val batch = dirty.values.toList()
        storage.save(batch)
        batch.forEach { dirty.remove(it.id, it) }
    }

    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
        }
        io.shutdown()
        try {
            check(io.awaitTermination(20, TimeUnit.SECONDS)) { "Timed out while stopping incognito storage" }
            flush()
        } finally {
            storage.close()
        }
    }
}
