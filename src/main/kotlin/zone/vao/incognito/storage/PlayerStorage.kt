package zone.vao.incognito.storage

interface PlayerStorage : AutoCloseable {
    fun loadAll(): List<PlayerRecord>
    fun save(records: Collection<PlayerRecord>)
}
