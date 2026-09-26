package zone.vao.incognito.network

import org.bukkit.configuration.file.FileConfiguration

data class NetworkConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val database: Int,
    val ssl: Boolean,
    val prefix: String,
    val sessionTimeout: Long,
) {
    init {
        require(host.matches(Regex("[A-Za-z0-9.:-]+"))) { "Invalid network.host" }
        require(port in 1..65535 && database in 0..15) { "Invalid network port or database" }
        require(prefix.matches(Regex("[A-Za-z0-9_:.-]{0,40}"))) { "Invalid network.key-prefix" }
        require(sessionTimeout in 15..86_400) { "network.session-timeout must be between 15 and 86400 seconds" }
    }

    companion object {
        fun load(config: FileConfiguration): NetworkConfig = NetworkConfig(
            config.getBoolean("network.enabled", false),
            config.getString("network.host", "localhost")!!,
            config.getInt("network.port", 6379),
            config.getString("network.username", "")!!,
            config.getString("network.password", "")!!,
            config.getInt("network.database", 0),
            config.getBoolean("network.ssl", false),
            config.getString("network.key-prefix", "incognito:")!!,
            config.getLong("network.session-timeout", 60),
        )
    }
}
