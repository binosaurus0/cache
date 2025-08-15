import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Base Cache interface defining core caching operations
 */
interface Cache {
    val size: Int
    
    operator fun set(key: Any, value: Any)
    operator fun get(key: Any): Any?
    
    fun remove(key: Any): Any?
    fun clear()
    fun containsKey(key: Any): Boolean
}

/**
 * Simple cache implementation that stores items forever until manually removed
 */
class PerpetualCache : Cache {
    private val cache = HashMap<Any, Any>()

    override val size: Int
        get() = cache.size

    override fun set(key: Any, value: Any) {
        cache[key] = value
    }

    override fun get(key: Any): Any? = cache[key]

    override fun remove(key: Any): Any? = cache.remove(key)

    override fun clear() = cache.clear()
    
    override fun containsKey(key: Any): Boolean = cache.containsKey(key)
}

/**
 * LRU (Least Recently Used) cache implementation using decorator pattern
 * Keeps only a limited number of most recently accessed items
 */
class LRUCache(
    private val delegate: Cache, 
    private val maxSize: Int = DEFAULT_SIZE
) : Cache by delegate {
    
    private val keyMap = object : LinkedHashMap<Any, Any>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Any>): Boolean {
            val tooManyCachedItems = size > maxSize
            if (tooManyCachedItems) eldestKeyToRemove = eldest.key
            return tooManyCachedItems
        }
    }

    private var eldestKeyToRemove: Any? = null

    override fun set(key: Any, value: Any) {
        delegate[key] = value
        cycleKeyMap(key)
    }

    override fun get(key: Any): Any? {
        keyMap[key] // Mark as recently used
        return delegate[key]
    }

    override fun clear() {
        keyMap.clear()
        delegate.clear()
    }

    private fun cycleKeyMap(key: Any) {
        keyMap[key] = PRESENT
        eldestKeyToRemove?.let { 
            delegate.remove(it)
        }
        eldestKeyToRemove = null
    }

    companion object {
        private const val DEFAULT_SIZE = 100
        private const val PRESENT = true
    }
}

/**
 * Expirable cache implementation that clears all entries after a specified interval
 */
class ExpirableCache(
    private val delegate: Cache,
    private val flushIntervalMs: Long = TimeUnit.MINUTES.toMillis(1)
) : Cache by delegate {
    
    private var lastFlushTime = System.nanoTime()

    override val size: Int
        get() {
            recycle()
            return delegate.size
        }

    override fun get(key: Any): Any? {
        recycle()
        return delegate[key]
    }

    override fun remove(key: Any): Any? {
        recycle()
        return delegate.remove(key)
    }

    override fun set(key: Any, value: Any) {
        recycle()
        delegate[key] = value
    }

    private fun recycle() {
        val currentTime = System.nanoTime()
        val shouldRecycle = currentTime - lastFlushTime >= TimeUnit.MILLISECONDS.toNanos(flushIntervalMs)
        if (shouldRecycle) {
            delegate.clear()
            lastFlushTime = currentTime
        }
    }
}

/**
 * Advanced cache with per-entry TTL (Time To Live) support
 */
class TTLCache(
    private val delegate: Cache,
    private val defaultTTLMs: Long = TimeUnit.MINUTES.toMillis(5)
) : Cache by delegate {
    
    private data class CacheEntry(val value: Any, val expiryTime: Long)
    private val entryMap = HashMap<Any, CacheEntry>()

    override val size: Int
        get() {
            cleanupExpired()
            return entryMap.size
        }

    override fun set(key: Any, value: Any) {
        val expiryTime = System.currentTimeMillis() + defaultTTLMs
        entryMap[key] = CacheEntry(value, expiryTime)
        delegate[key] = value
    }

    fun set(key: Any, value: Any, ttlMs: Long) {
        val expiryTime = System.currentTimeMillis() + ttlMs
        entryMap[key] = CacheEntry(value, expiryTime)
        delegate[key] = value
    }

    override fun get(key: Any): Any? {
        val entry = entryMap[key] ?: return null
        return if (entry.expiryTime > System.currentTimeMillis()) {
            delegate[key]
        } else {
            remove(key)
            null
        }
    }

    override fun remove(key: Any): Any? {
        entryMap.remove(key)
        return delegate.remove(key)
    }

    override fun clear() {
        entryMap.clear()
        delegate.clear()
    }

    override fun containsKey(key: Any): Boolean {
        val entry = entryMap[key] ?: return false
        return if (entry.expiryTime > System.currentTimeMillis()) {
            delegate.containsKey(key)
        } else {
            remove(key)
            false
        }
    }

    private fun cleanupExpired() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = entryMap.entries
            .filter { it.value.expiryTime <= currentTime }
            .map { it.key }
        
        expiredKeys.forEach { remove(it) }
    }
}

/**
 * Thread-safe cache wrapper
 */
class SynchronizedCache(private val delegate: Cache) : Cache {
    private val lock = Any()

    override val size: Int
        get() = synchronized(lock) { delegate.size }

    override fun set(key: Any, value: Any) {
        synchronized(lock) { delegate[key] = value }
    }

    override fun get(key: Any): Any? {
        return synchronized(lock) { delegate[key] }
    }

    override fun remove(key: Any): Any? {
        return synchronized(lock) { delegate.remove(key) }
    }

    override fun clear() {
        synchronized(lock) { delegate.clear() }
    }

    override fun containsKey(key: Any): Boolean {
        return synchronized(lock) { delegate.containsKey(key) }
    }
}

/**
 * Cache builder for easy configuration
 */
class CacheBuilder {
    private var maxSize: Int? = null
    private var ttlMs: Long? = null
    private var flushIntervalMs: Long? = null
    private var threadSafe = false

    fun maxSize(size: Int) = apply { maxSize = size }
    fun ttl(ttl: Long, unit: TimeUnit = TimeUnit.MILLISECONDS) = apply { 
        ttlMs = unit.toMillis(ttl) 
    }
    fun flushInterval(interval: Long, unit: TimeUnit = TimeUnit.MILLISECONDS) = apply { 
        flushIntervalMs = unit.toMillis(interval) 
    }
    fun threadSafe() = apply { threadSafe = true }

    fun build(): Cache {
        var cache: Cache = PerpetualCache()

        // Apply LRU if max size is specified
        maxSize?.let { size ->
            cache = LRUCache(cache, size)
        }

        // Apply TTL if specified
        ttlMs?.let { ttl ->
            cache = TTLCache(cache, ttl)
        }

        // Apply expirable cache if flush interval is specified
        flushIntervalMs?.let { interval ->
            cache = ExpirableCache(cache, interval)
        }

        // Apply thread safety if requested
        if (threadSafe) {
            cache = SynchronizedCache(cache)
        }

        return cache
    }
}

// Extension functions for type-safe operations
inline fun <reified T> Cache.getTyped(key: Any): T? {
    return get(key) as? T
}

inline fun <reified K, reified V> Cache.setTyped(key: K, value: V) {
    set(key as Any, value as Any)
}

// Usage examples and factory methods
object CacheFactory {
    fun perpetual() = PerpetualCache()
    
    fun lru(maxSize: Int = 100) = LRUCache(PerpetualCache(), maxSize)
    
    fun expirable(flushIntervalMs: Long = TimeUnit.MINUTES.toMillis(1)) = 
        ExpirableCache(PerpetualCache(), flushIntervalMs)
    
    fun ttl(ttlMs: Long = TimeUnit.MINUTES.toMillis(5)) = 
        TTLCache(PerpetualCache(), ttlMs)
    
    fun builder() = CacheBuilder()
}

// Demo usage
fun main() {
    println("=== Cache Demo ===")
    
    // Simple perpetual cache
    val simpleCache = CacheFactory.perpetual()
    simpleCache["key1"] = "value1"
    println("Simple cache: ${simpleCache["key1"]}")
    
    // LRU Cache
    val lruCache = CacheFactory.lru(maxSize = 3)
    lruCache["a"] = "A"
    lruCache["b"] = "B" 
    lruCache["c"] = "C"
    lruCache["d"] = "D" // This should evict "a"
    println("LRU cache size: ${lruCache.size}")
    println("LRU cache contains 'a': ${lruCache.containsKey("a")}")
    
    // TTL Cache
    val ttlCache = CacheFactory.ttl(TimeUnit.SECONDS.toMillis(2))
    ttlCache["temp"] = "temporary value"
    println("TTL cache before expiry: ${ttlCache["temp"]}")
    
    // Builder pattern
    val advancedCache = CacheFactory.builder()
        .maxSize(50)
        .ttl(30, TimeUnit.SECONDS)
        .threadSafe()
        .build()
    
    advancedCache["complex"] = "Complex cache with multiple features"
    println("Advanced cache: ${advancedCache["complex"]}")
    
    // Type-safe operations
    advancedCache.setTyped("number", 42)
    val number: Int? = advancedCache.getTyped("number")
    println("Type-safe retrieval: $number")
}