package maryk.datastore.shared

import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import kotlin.math.max

/**
 * Cache to store previously retrieved values, so they take less time to decode and take less memory
 * because previous instance is reused.
 */
class Cache(
    private val maxKeysPerDb: Int = DEFAULT_MAX_KEYS_PER_DB,
    private val maxSizeBytesPerKey: Int = DEFAULT_CACHE_SIZE_BYTES_PER_KEY
) {
    private val cachePerDb = mutableMapOf<UInt, DbCache>()

    init {
        require(maxKeysPerDb > 0) { "maxKeysPerDb should be greater than zero" }
        require(maxSizeBytesPerKey > 0) { "maxSizeBytesPerKey should be greater than zero" }
    }

    /**
     * Read value from the cache if it exists and is of same version, or read it from store and cache it if
     * it is not of same version or does not exist in cache
     */
    fun readValue(
        dbIndex: UInt,
        key: Key<*>,
        reference: IsPropertyReferenceForCache<*, *>,
        version: ULong,
        valueReader: () -> Any?
    ): Any? {
        val dbCache = cachePerDb.getOrPut(dbIndex) {
            DbCache(maxKeys = maxKeysPerDb, maxSizeBytesPerKey = maxSizeBytesPerKey)
        }
        val value = dbCache.get(key)?.get(reference)

        return if (value != null && version == value.version) {
            value.value
        } else {
            valueReader().also { readValue ->
                if (value == null || value.version < version) {
                    dbCache.getOrCreate(key).put(reference, CachedValue(version, readValue))
                }
            }
        }
    }

    /** Delete [key] from the table with [dbIndex] */
    fun delete(dbIndex: UInt, key: Key<*>) {
        val dbCache = cachePerDb[dbIndex] ?: return
        dbCache.delete(key)
        if (dbCache.isEmpty()) {
            cachePerDb.remove(dbIndex)
        }
    }
}

private const val DEFAULT_MAX_KEYS_PER_DB = 1024
private const val DEFAULT_CACHE_SIZE_BYTES_PER_KEY = 128 * 1024
private const val APPROX_ENTRY_SIZE_BYTES = 1024

private class DbCache(
    private val maxKeys: Int,
    private val maxSizeBytesPerKey: Int
) {
    private val values = mutableMapOf<Key<*>, ReferenceCache>()
    private val order = LinkedHashSet<Key<*>>()

    fun get(key: Key<*>): ReferenceCache? {
        val cache = values[key] ?: return null
        order.remove(key)
        order.add(key)
        return cache
    }

    fun getOrCreate(key: Key<*>): ReferenceCache {
        val cache = values.getOrPut(key) {
            ReferenceCache(maxSizeBytes = maxSizeBytesPerKey)
        }
        order.remove(key)
        order.add(key)

        if (values.size > maxKeys) {
            val iterator = order.iterator()
            if (iterator.hasNext()) {
                val oldest = iterator.next()
                iterator.remove()
                values.remove(oldest)
            }
        }

        return cache
    }

    fun delete(key: Key<*>) {
        values.remove(key)
        order.remove(key)
    }

    fun isEmpty() = values.isEmpty()
}

private class ReferenceCache(maxSizeBytes: Int) {
    private val maxEntries = max(1, maxSizeBytes / APPROX_ENTRY_SIZE_BYTES)
    private val values = mutableMapOf<IsPropertyReferenceForCache<*, *>, CachedValue>()
    private val order = LinkedHashSet<IsPropertyReferenceForCache<*, *>>()

    fun get(reference: IsPropertyReferenceForCache<*, *>): CachedValue? {
        val cached = values[reference] ?: return null
        order.remove(reference)
        order.add(reference)
        return cached
    }

    fun put(reference: IsPropertyReferenceForCache<*, *>, value: CachedValue) {
        if (values.put(reference, value) != null) {
            order.remove(reference)
        }
        order.add(reference)

        if (values.size > maxEntries) {
            val iterator = order.iterator()
            if (iterator.hasNext()) {
                val oldest = iterator.next()
                iterator.remove()
                values.remove(oldest)
            }
        }
    }
}
