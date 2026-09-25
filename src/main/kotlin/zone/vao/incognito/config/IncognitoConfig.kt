package zone.vao.incognito.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import zone.vao.incognito.identity.SessionAliases
import java.io.File

data class IncognitoConfig(
    val names: Boolean,
    val skin: Boolean,
    val coordinates: Boolean,
    val namesTabComplete: Boolean,
    val hideRealName: Boolean,
    val aliasFormat: String,
    val heads: Boolean,
    val books: Boolean,
    val coordinatesTabComplete: Boolean,
    val placeholders: Boolean,
    val coordinatePatterns: List<Regex>,
    val texture: String,
    val signature: String,
    val debug: Boolean,
    val messages: Messages,
    val joinQuit: JoinQuitConfig,
) {
    companion object {
        fun sync(plugin: JavaPlugin) {
            val target = File(plugin.dataFolder, "config.yml")
            if (!target.exists()) {
                plugin.saveResource("config.yml", false)
                return
            }
            val resource = plugin.getResource("config.yml") ?: return
            val defaults = resource.bufferedReader(Charsets.UTF_8).use { YamlConfiguration.loadConfiguration(it) }
            val current = YamlConfiguration.loadConfiguration(target)
            val missing = defaults.getKeys(true)
                .filterNot { defaults.isConfigurationSection(it) }
                .filterNot { current.contains(it) }
            if (missing.isEmpty()) return
            for (key in missing) {
                current.set(key, defaults.get(key))
                current.setComments(key, defaults.getComments(key))
                current.setInlineComments(key, defaults.getInlineComments(key))
            }
            runCatching { current.save(target) }
                .onSuccess { plugin.logger.info("Added ${missing.size} new default value(s) to config.yml.") }
                .onFailure { plugin.logger.warning("Failed to update config.yml with new defaults: ${it.message}") }
        }

        fun load(config: FileConfiguration): IncognitoConfig {
            val texture = config.getString("skin.value", "")!!.trim()
            val signature = config.getString("skin.signature", "")!!.trim()
            require(texture.isEmpty() == signature.isEmpty()) { "Provide skin.value and skin.signature together" }
            val format = config.getString("names.format", "Anon_{random}")!!.trim()
            SessionAliases(format)
            return IncognitoConfig(
                config.getBoolean("names.enabled", true),
                config.getBoolean("skin.enabled", true),
                config.getBoolean("coordinates.enabled", true),
                config.getBoolean("names.tabcomplete", true),
                config.getBoolean("names.hide_realname", true),
                format,
                config.getBoolean("heads", true),
                config.getBoolean("books", true),
                config.getBoolean("coordinates.tabcomplete", true),
                config.getBoolean("placeholders.enabled", true),
                config.getStringList("coordinates.patterns").map { Regex(it, RegexOption.IGNORE_CASE) },
                texture,
                signature,
                config.getBoolean("debug", false),
                Messages(config),
                JoinQuitConfig.load(config),
            )
        }
    }
}
