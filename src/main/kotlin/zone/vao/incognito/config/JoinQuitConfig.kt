package zone.vao.incognito.config

import org.bukkit.configuration.file.FileConfiguration

data class JoinQuitConfig(val mode: Mode, val defaultShow: Boolean, val allowToggle: Boolean) {
    enum class Mode { PLAYER, SHOW, HIDE }

    val canToggle: Boolean get() = mode == Mode.PLAYER && allowToggle

    fun shows(preference: Boolean?): Boolean = when (mode) {
        Mode.SHOW -> true
        Mode.HIDE -> false
        Mode.PLAYER -> preference ?: defaultShow
    }

    companion object {
        fun load(config: FileConfiguration): JoinQuitConfig {
            val mode = config.getString("join-quit.mode", "player")!!.uppercase()
            require(Mode.entries.any { it.name == mode }) { "join-quit.mode must be player, show or hide" }
            return JoinQuitConfig(
                Mode.valueOf(mode),
                config.getBoolean("join-quit.default-show", true),
                config.getBoolean("join-quit.allow-toggle", true),
            )
        }
    }
}
