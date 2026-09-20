package zone.vao.incognito.config

import org.bukkit.configuration.file.FileConfiguration

data class IncognitoConfig(
    val names: Boolean,
    val skin: Boolean,
    val coordinates: Boolean,
    val namesTabComplete: Boolean,
    val coordinatesTabComplete: Boolean,
    val placeholders: Boolean,
    val coordinatePatterns: List<Regex>,
    val texture: String,
    val signature: String,
    val debug: Boolean,
    val messages: Messages,
) {
    companion object {
        fun load(config: FileConfiguration): IncognitoConfig {
            val texture = config.getString("skin.value", "")!!.trim()
            val signature = config.getString("skin.signature", "")!!.trim()
            require(texture.isEmpty() == signature.isEmpty()) { "Provide skin.value and skin.signature together" }
            return IncognitoConfig(
                config.getBoolean("names.enabled", true),
                config.getBoolean("skin.enabled", true),
                config.getBoolean("coordinates.enabled", true),
                config.getBoolean("names.tabcomplete", true),
                config.getBoolean("coordinates.tabcomplete", true),
                config.getBoolean("placeholders.enabled", true),
                config.getStringList("coordinates.patterns").map { Regex(it, RegexOption.IGNORE_CASE) },
                texture,
                signature,
                config.getBoolean("debug", false),
                Messages(config),
            )
        }
    }
}
