package zone.vao.incognito.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID

class SqlPlayerStorage(config: HikariConfig, prefix: String, type: String) : PlayerStorage {

    private val table = "${prefix}players".also { require(it.matches(Regex("[A-Za-z0-9_]+"))) }
    private val source = HikariDataSource(config)
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
}
