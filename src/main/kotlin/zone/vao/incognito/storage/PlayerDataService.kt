package zone.vao.incognito.storage

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

class PlayerDataService(private val storage: PlayerStorage, private val logger: Logger) : AutoCloseable {

    private val cache = ConcurrentHashMap<UUID, PlayerRecord>()
    private val dirty = ConcurrentHashMap<UUID, PlayerRecord>()
    private val activeHistory = ConcurrentHashMap<UUID, HistorySession>()
    private val dirtyHistory = ConcurrentHashMap<UUID, HistorySession>()
    private val historyStorage: SessionHistoryStorage get() = storage as? SessionHistoryStorage ?: error("Storage does not support session history")
    private val writes = Any()
    private val io = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "incognito-storage").apply { isDaemon = true } }
    @Volatile private var closed = false
    @Volatile var listener: ((PlayerRecord) -> Unit)? = null

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
        listener?.invoke(record)
    }

    fun receive(record: PlayerRecord) {
        if (closed || cache[record.id] == record) return
        cache[record.id] = record
        dirty[record.id] = record
    }

    @Synchronized
    fun startSession(playerId: UUID, realName: String, alias: String, sessionId: UUID, server: String) {
        check(!closed) { "Incognito storage is closed" }
        val previous = activeHistory[playerId]
        if (previous?.sessionId == sessionId && previous.alias == alias) return
        endSession(playerId)
        val session = HistorySession(UUID.randomUUID(), sessionId, playerId, realName, alias, server, System.currentTimeMillis())
        activeHistory[playerId] = session
        dirtyHistory[session.id] = session
    }

    @Synchronized
    fun endSession(playerId: UUID) {
        val session = activeHistory.remove(playerId) ?: return
        dirtyHistory[session.id] = session.copy(endedAt = maxOf(session.startedAt, System.currentTimeMillis()))
    }

    @Synchronized
    fun history(lookup: HistoryLookup, value: String, page: Int): CompletableFuture<List<HistorySession>> {
        check(!closed) { "Incognito storage is closed" }
        return CompletableFuture.supplyAsync({
            flush()
            historyStorage.history(lookup, value, page)
        }, io)
    }

    fun flush() = synchronized(writes) {
        val batch = dirty.values.toList()
        storage.save(batch)
        batch.forEach { dirty.remove(it.id, it) }
        val sessions = dirtyHistory.values.toList()
        if (sessions.isNotEmpty()) historyStorage.saveHistory(sessions)
        sessions.forEach { dirtyHistory.remove(it.id, it) }
    }

    override fun close() {
        synchronized(this) {
            if (closed) return
            activeHistory.keys.toList().forEach(::endSession)
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
