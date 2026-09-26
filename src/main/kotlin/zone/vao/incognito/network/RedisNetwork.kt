package zone.vao.incognito.network

import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.params.SetParams
import zone.vao.incognito.storage.PlayerRecord
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class RedisNetwork(
    private val config: NetworkConfig,
    private val logger: Logger,
    private val onRecord: (PlayerRecord) -> Unit,
) : AutoCloseable {

    private val instance = UUID.randomUUID().toString()
    private val channel = "${config.prefix}records"
    private val address = HostAndPort(config.host, config.port)
    private val pool = JedisPool(address, client(5000))
    private val owned = ConcurrentHashMap<UUID, String>()
    private val publisher = Executors.newSingleThreadExecutor { task -> Thread(task, "incognito-redis-publish").apply { isDaemon = true } }
    @Volatile private var running = true
    @Volatile private var subscriber: Jedis? = null
    @Volatile private var pubSub: JedisPubSub? = null
    private val thread = Thread(::subscribe, "incognito-redis").apply {
        isDaemon = true
        start()
    }

    init {
        runCatching { pool.resource.use { it.ping() } }
            .onSuccess { logger.info("Connected to Redis at ${config.host}:${config.port}; incognito sessions are shared across the network.") }
            .onFailure { logger.warning("Cannot reach Redis at ${config.host}:${config.port} (${it.javaClass.simpleName}); players get local sessions until it is available.") }
    }

    fun claim(id: UUID, create: (taken: (String) -> Boolean) -> NetworkSession): NetworkClaim = pool.resource.use { redis ->
        repeat(8) {
            redis.get(session(id))?.let(NetworkCodec::session)?.let { existing ->
                redis.expire(session(id), config.sessionTimeout)
                redis.expire(alias(existing.alias), config.sessionTimeout)
                owned[id] = existing.alias
                return NetworkClaim(existing, true)
            }
            val created = create { redis.exists(alias(it)) }
            if (redis.set(alias(created.alias), id.toString(), SetParams().nx().ex(config.sessionTimeout)) == null) return@repeat
            if (redis.set(session(id), NetworkCodec.session(created), SetParams().nx().ex(config.sessionTimeout)) != null) {
                owned[id] = created.alias
                return NetworkClaim(created, false)
            }
            redis.del(alias(created.alias))
        }
        error("Cannot claim a network session for $id")
    }

    fun release(id: UUID) {
        owned.remove(id)
        pool.resource.use { redis ->
            val existing = redis.get(session(id))?.let(NetworkCodec::session) ?: return
            if (redis.get(alias(existing.alias)) == id.toString()) redis.del(alias(existing.alias))
            redis.del(session(id))
        }
    }

    fun forget(id: UUID) {
        owned.remove(id)
    }

    fun refresh(ids: Collection<UUID>) {
        if (ids.isEmpty()) return
        pool.resource.use { redis ->
            val pipeline = redis.pipelined()
            ids.forEach { id ->
                pipeline.expire(session(id), config.sessionTimeout)
                owned[id]?.let { pipeline.expire(alias(it), config.sessionTimeout) }
            }
            pipeline.sync()
        }
    }

    fun publish(record: PlayerRecord) {
        if (!running) return
        publisher.execute {
            runCatching { pool.resource.use { it.publish(channel, NetworkCodec.record(instance, record)) } }
                .onFailure { logger.warning("Cannot publish an incognito state change to Redis (${it.javaClass.simpleName}).") }
        }
    }

    private fun subscribe() {
        var warned = false
        while (running) {
            try {
                val jedis = Jedis(address, client(0))
                subscriber = jedis
                val listener = object : JedisPubSub() {
                    override fun onMessage(channel: String, message: String) {
                        val (sender, record) = NetworkCodec.record(message) ?: return
                        if (sender != instance) runCatching { onRecord(record) }
                    }
                }
                pubSub = listener
                warned = false
                jedis.subscribe(listener, channel)
            } catch (error: Exception) {
                if (running && !warned) {
                    logger.warning("Redis subscription lost (${error.javaClass.simpleName}); retrying every 5s.")
                    warned = true
                }
            }
            if (running) runCatching { Thread.sleep(5000) }
        }
    }

    private fun client(socketTimeout: Int) = DefaultJedisClientConfig.builder()
        .connectionTimeoutMillis(5000)
        .socketTimeoutMillis(socketTimeout)
        .database(config.database)
        .ssl(config.ssl)
        .apply {
            if (config.username.isNotEmpty()) user(config.username)
            if (config.password.isNotEmpty()) password(config.password)
        }
        .build()

    private fun session(id: UUID) = "${config.prefix}session:$id"

    private fun alias(alias: String) = "${config.prefix}alias:${alias.lowercase()}"

    override fun close() {
        running = false
        publisher.shutdown()
        runCatching { publisher.awaitTermination(5, TimeUnit.SECONDS) }
        runCatching { pubSub?.unsubscribe() }
        runCatching { subscriber?.close() }
        thread.interrupt()
        pool.close()
    }
}
