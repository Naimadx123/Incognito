package zone.vao.incognito.storage

import java.util.UUID

data class HistorySession(
    val id: UUID,
    val sessionId: UUID,
    val playerId: UUID,
    val realName: String,
    val alias: String,
    val server: String,
    val startedAt: Long,
    val endedAt: Long? = null,
)

enum class HistoryLookup { ALIAS, PLAYER }

interface SessionHistoryStorage {
    fun saveHistory(sessions: Collection<HistorySession>)
    fun history(lookup: HistoryLookup, value: String, page: Int): List<HistorySession>
}
