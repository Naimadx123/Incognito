package zone.vao.incognito.storage

import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID
import java.util.logging.Logger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlayerStorageTest {

    private val config = StorageConfig.load(YamlConfiguration())
    private val logger = Logger.getLogger("IncognitoStorageTest")

    @Test
    fun `sqlite restores active and disabled records before cache reads`() {
        val folder = createTempDirectory("incognito-storage").toFile()
        val active = PlayerRecord(UUID.randomUUID(), "ExamplePlayer", "Anon_0123456789", true)
        val disabled = PlayerRecord(UUID.randomUUID(), "OtherPlayer", "Anon_9876543210", false)
        try {
            PlayerDataService(StorageFactory.create(folder, config), logger).use {
                it.save(active)
                it.save(disabled)
                assertEquals(active, it.get(active.id))
                assertEquals(listOf(active), it.enabled())
            }
            PlayerDataService(StorageFactory.create(folder, config), logger).use {
                assertEquals(active, it.get(active.id))
                assertEquals(disabled, it.get(disabled.id))
                it.save(active.copy(enabled = false))
                assertTrue(it.enabled().isEmpty())
            }
            StorageFactory.create(folder, config).use {
                val records = it.loadAll()
                assertEquals(2, records.size)
                assertTrue(records.none { record -> record.enabled })
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `failed writes retain the latest changes for retry`() {
        var reject = true
        var closed = false
        val saved = mutableListOf<PlayerRecord>()
        val storage = object : PlayerStorage {
            override fun loadAll(): List<PlayerRecord> = emptyList()
            override fun save(records: Collection<PlayerRecord>) {
                if (reject) error("Unavailable storage")
                saved.addAll(records)
            }
            override fun close() { closed = true }
        }
        val record = PlayerRecord(UUID.randomUUID(), "ExamplePlayer", "Anon_0123456789", true)
        PlayerDataService(storage, logger).use {
            it.save(record)
            assertFailsWith<IllegalStateException> { it.flush() }
            it.save(record.copy(enabled = false))
            assertFalse(it.get(record.id)!!.enabled)
            reject = false
            it.flush()
            assertEquals(listOf(record.copy(enabled = false)), saved)
        }
        assertTrue(closed)
    }

    @Test
    fun `postgres accepts both type names and preserves explicit ports`() {
        for (type in listOf("postgres", "postgresql", "POSTGRES")) {
            val yaml = YamlConfiguration().apply { set("storage.type", type) }
            assertEquals(5432, StorageConfig.load(yaml).port)
            assertEquals(type.lowercase(), StorageConfig.load(yaml).type)
            yaml.set("storage.port", 15432)
            assertEquals(15432, StorageConfig.load(yaml).port)
        }
        assertEquals(3306, config.port)
    }

    @Test
    fun `invalid storage configuration is rejected instead of silently selecting sqlite`() {
        assertFailsWith<IllegalArgumentException> { config.copy(type = "unknown") }
        assertFailsWith<IllegalArgumentException> { config.copy(tablePrefix = "players; DROP TABLE players") }
        assertFailsWith<IllegalArgumentException> { config.copy(database = "database?user=other") }
    }
}
