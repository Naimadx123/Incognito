package zone.vao.incognito.storage

import org.bukkit.configuration.file.FileConfiguration

data class StorageConfig(
    val type: String,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val tablePrefix: String,
    val poolSize: Int,
) {
    init {
        require(type in setOf("sqlite", "mysql", "postgres", "postgresql")) { "storage.type must be sqlite, mysql, postgres or postgresql" }
        require(tablePrefix.matches(Regex("[A-Za-z0-9_]{0,40}"))) { "Invalid storage.table-prefix" }
        require(port in 1..65535 && poolSize in 1..32) { "Invalid storage port or pool size" }
        require(database.matches(Regex("[A-Za-z0-9_]+"))) { "Invalid storage.database" }
        require(host.matches(Regex("[A-Za-z0-9.:-]+"))) { "Invalid storage.host" }
    }

    companion object {
        fun load(config: FileConfiguration): StorageConfig = StorageConfig(
            config.getString("storage.type", "sqlite")!!.lowercase(),
            config.getString("storage.host", "localhost")!!,
            config.getInt("storage.port", if (config.getString("storage.type")?.lowercase() in setOf("postgres", "postgresql")) 5432 else 3306),
            config.getString("storage.database", "incognito")!!,
            config.getString("storage.username", "incognito")!!,
            config.getString("storage.password", "")!!,
            config.getString("storage.table-prefix", "incognito_")!!,
            config.getInt("storage.pool-size", 4),
        )
    }
}
