package zone.vao.incognito.network

import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.storage.PlayerRecord
import java.util.UUID

object NetworkCodec {

    fun session(session: NetworkSession): String =
        listOf(session.alias, session.maskedId, session.offset.x, session.offset.z).joinToString("|")

    fun session(raw: String): NetworkSession? = runCatching {
        val parts = raw.split('|')
        require(parts.size == 4)
        NetworkSession(parts[0], UUID.fromString(parts[1]), CoordinateOffset(parts[2].toInt(), parts[3].toInt()))
    }.getOrNull()

    fun record(sender: String, record: PlayerRecord): String = listOf(
        sender, record.id, record.realName, if (record.enabled) "1" else "0",
        when (record.showJoinQuit) { null -> ""; true -> "1"; false -> "0" }, record.lastAlias.orEmpty(),
    ).joinToString("|")

    fun record(raw: String): Pair<String, PlayerRecord>? = runCatching {
        val parts = raw.split('|')
        require(parts.size == 6)
        parts[0] to PlayerRecord(
            UUID.fromString(parts[1]), parts[2], parts[3] == "1",
            if (parts[4].isEmpty()) null else parts[4] == "1", parts[5].ifEmpty { null },
        )
    }.getOrNull()
}
