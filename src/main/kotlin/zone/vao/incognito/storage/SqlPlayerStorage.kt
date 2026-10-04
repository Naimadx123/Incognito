package zone.vao.incognito.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID

class SqlPlayerStorage(config: HikariConfig, prefix: String, type: String) : PlayerStorage, SessionHistoryStorage {

    private val table = "${prefix}players".also { require(it.matches(Regex("[A-Za-z0-9_]+"))) }
    private val source = HikariDataSource(config)
    private val historyTable = "${prefix}sessions"
    private val historyColumns = listOf("id", "session_id", "player_uuid", "real_name", "name_key", "alias", "alias_key", "server_id", "started_at", "ended_at")
    private val historyUpsert = "INSERT INTO $historyTable (${historyColumns.joinToString()}) VALUES (${historyColumns.joinToString { "?" }}) " +
        if (type != "mysql") "ON CONFLICT(id) DO UPDATE SET ended_at = excluded.ended_at WHERE $historyTable.ended_at IS NULL"
        else "ON DUPLICATE KEY UPDATE ended_at = COALESCE(ended_at, VALUES(ended_at))"
    private val columns = listOf("uuid", "real_name", "enabled", "show_join_quit", "last_alias")
    private val upsert = "INSERT INTO $table (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }}) " +
        if (type != "mysql") "ON CONFLICT(uuid) DO UPDATE SET " + columns.drop(1).joinToString { "$it = excluded.$it" }
        else "ON DUPLICATE KEY UPDATE " + columns.drop(1).joinToString { "$it = VALUES($it)" }

    init {
        try {
            source.connection.use { connection ->
                connection.createStatement().use {
                    it.executeUpdate("CREATE TABLE IF NOT EXISTS $table (uuid VARCHAR(36) PRIMARY KEY, real_name VARCHAR(16) NOT NULL, enabled INT NOT NULL)")
                    val existing = it.executeQuery("SELECT * FROM $table WHERE 1 = 0").use { rows ->
                        (1..rows.metaData.columnCount).map { index -> rows.metaData.getColumnName(index).lowercase() }.toSet()
                    }
                    if ("show_join_quit" !in existing) it.executeUpdate("ALTER TABLE $table ADD COLUMN show_join_quit INT NULL")
                    if ("last_alias" !in existing) it.executeUpdate("ALTER TABLE $table ADD COLUMN last_alias VARCHAR(16) NULL")
                    val legacy = it.executeQuery("SELECT * FROM $table WHERE 1 = 0").use { rows ->
                        (1..rows.metaData.columnCount).any { index -> rows.metaData.getColumnName(index).equals("alias", true) }
                    }
                    if (legacy) it.executeUpdate("ALTER TABLE $table DROP COLUMN alias")
                    it.executeUpdate("CREATE TABLE IF NOT EXISTS $historyTable (id VARCHAR(36) PRIMARY KEY, session_id VARCHAR(36) NOT NULL, player_uuid VARCHAR(36) NOT NULL, real_name VARCHAR(16) NOT NULL, name_key VARCHAR(16) NOT NULL, alias VARCHAR(16) NOT NULL, alias_key VARCHAR(16) NOT NULL, server_id VARCHAR(64) NOT NULL, started_at BIGINT NOT NULL, ended_at BIGINT NULL)")
                    val indexedTable = if (type in setOf("postgres", "postgresql")) historyTable.lowercase() else historyTable
                    val indexes = connection.metaData.getIndexInfo(connection.catalog, null, indexedTable, false, false).use { rows ->
                        buildSet { while (rows.next()) rows.getString("INDEX_NAME")?.let { name -> add(name.lowercase()) } }
                    }
                    for (column in listOf("alias_key", "name_key", "player_uuid")) {
                        val index = "${historyTable}_$column"
                        if (index.lowercase() !in indexes) {
                            try {
                                it.executeUpdate("CREATE INDEX ${if (type == "mysql") "" else "IF NOT EXISTS "}$index ON $historyTable ($column, started_at)")
                            } catch (error: java.sql.SQLException) {
                                if (type != "mysql" || error.errorCode != 1061) throw error
                            }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            source.close()
            throw error
        }
    }

    override fun loadAll(): List<PlayerRecord> = source.connection.use { connection ->
        connection.prepareStatement("SELECT ${columns.joinToString()} FROM $table").use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(PlayerRecord(
                        UUID.fromString(rows.getString("uuid")), rows.getString("real_name"), rows.getInt("enabled") != 0,
                        rows.getInt("show_join_quit").let { if (rows.wasNull()) null else it != 0 },
                        rows.getString("last_alias"),
                    ))
                }
            }
        }
    }

    override fun save(records: Collection<PlayerRecord>) {
        if (records.isEmpty()) return
        source.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(upsert).use { statement ->
                    records.forEach {
                        statement.setString(1, it.id.toString())
                        statement.setString(2, it.realName)
                        statement.setInt(3, if (it.enabled) 1 else 0)
                        if (it.showJoinQuit == null) statement.setNull(4, java.sql.Types.INTEGER)
                        else statement.setInt(4, if (it.showJoinQuit) 1 else 0)
                        statement.setString(5, it.lastAlias)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.commit()
            } catch (error: Exception) {
                runCatching { connection.rollback() }
                throw error
            }
        }
    }

    override fun close() = source.close()

    override fun saveHistory(sessions: Collection<HistorySession>) {
        if (sessions.isEmpty()) return
        source.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(historyUpsert).use { statement ->
                    sessions.forEach {
                        statement.setString(1, it.id.toString())
                        statement.setString(2, it.sessionId.toString())
                        statement.setString(3, it.playerId.toString())
                        statement.setString(4, it.realName)
                        statement.setString(5, it.realName.lowercase())
                        statement.setString(6, it.alias)
                        statement.setString(7, it.alias.lowercase())
                        statement.setString(8, it.server)
                        statement.setLong(9, it.startedAt)
                        if (it.endedAt == null) statement.setNull(10, java.sql.Types.BIGINT) else statement.setLong(10, it.endedAt)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.commit()
            } catch (error: Exception) {
                runCatching { connection.rollback() }
                throw error
            }
        }
    }

    override fun history(lookup: HistoryLookup, value: String, page: Int): List<HistorySession> {
        require(page in 1..1_000_000)
        val condition = if (lookup == HistoryLookup.ALIAS) "alias_key = ?"
            else "(name_key = ? OR player_uuid = ? OR player_uuid IN (SELECT uuid FROM $table WHERE LOWER(real_name) = ?))"
        return source.connection.use { connection ->
            connection.prepareStatement("SELECT ${historyColumns.joinToString()} FROM $historyTable WHERE $condition ORDER BY started_at DESC, id DESC LIMIT ? OFFSET ?").use { statement ->
                var index = 1
                statement.setString(index++, value.lowercase())
                if (lookup == HistoryLookup.PLAYER) {
                    statement.setString(index++, value.lowercase())
                    statement.setString(index++, value.lowercase())
                }
                statement.setInt(index++, 11)
                statement.setLong(index, (page - 1).toLong() * 10)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(HistorySession(
                            UUID.fromString(rows.getString("id")),
                            UUID.fromString(rows.getString("session_id")),
                            UUID.fromString(rows.getString("player_uuid")),
                            rows.getString("real_name"), rows.getString("alias"), rows.getString("server_id"),
                            rows.getLong("started_at"), rows.getLong("ended_at").let { if (rows.wasNull()) null else it },
                        ))
                    }
                }
            }
        }
    }
}
