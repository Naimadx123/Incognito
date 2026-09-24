package zone.vao.incognito.config

import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JoinQuitConfigTest {
    @Test
    fun `player mode uses the configured default only until a preference is saved`() {
        for (default in listOf(false, true)) {
            val config = JoinQuitConfig.load(YamlConfiguration().apply { set("join-quit.default-show", default) })
            assertEquals(default, config.shows(null))
            assertTrue(config.shows(true))
            assertFalse(config.shows(false))
            assertTrue(config.canToggle)
        }
    }

    @Test
    fun `forced modes override all player preferences and block toggling`() {
        for (mode in listOf("show", "hide")) {
            val config = JoinQuitConfig.load(YamlConfiguration().apply { set("join-quit.mode", mode) })
            for (preference in listOf(null, false, true)) assertEquals(mode == "show", config.shows(preference))
            assertFalse(config.canToggle)
        }
    }

    @Test
    fun `locking player mode preserves saved preferences`() {
        val config = JoinQuitConfig.load(YamlConfiguration().apply { set("join-quit.allow-toggle", false) })
        assertFalse(config.canToggle)
        assertTrue(config.shows(true))
        assertFalse(config.shows(false))
    }

    @Test
    fun `invalid mode fails instead of silently changing message visibility`() {
        assertFailsWith<IllegalArgumentException> {
            JoinQuitConfig.load(YamlConfiguration().apply { set("join-quit.mode", "hidden") })
        }
    }
}
