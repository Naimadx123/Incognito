package zone.vao.incognito.storage

import com.zaxxer.hikari.HikariConfig
import java.io.File

object StorageFactory {

    fun create(folder: File, config: StorageConfig): PlayerStorage {
        val hikari = HikariConfig().apply {
            poolName = "incognito-storage"
            connectionTimeout = 5000
            if (config.type == "sqlite") {
                folder.mkdirs()
                jdbcUrl = "jdbc:sqlite:${File(folder, "data.db").absolutePath}"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
                connectionInitSql = "PRAGMA busy_timeout=5000"
            } else {
                val postgres = config.type in setOf("postgres", "postgresql")
                jdbcUrl = "jdbc:${if (postgres) "postgresql" else "mysql"}://${config.host}:${config.port}/${config.database}"
                driverClassName = if (postgres) "org.postgresql.Driver" else "com.mysql.cj.jdbc.Driver"
                username = config.username
                password = config.password
                maximumPoolSize = config.poolSize
                addDataSourceProperty("connectTimeout", if (postgres) "5" else "5000")
                addDataSourceProperty("socketTimeout", if (postgres) "10" else "10000")
            }
        }
        return SqlPlayerStorage(hikari, config.tablePrefix, config.type)
    }
}
