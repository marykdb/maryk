package maryk.datastore.shared

import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import kotlin.math.max

/**
 * Cache to store previously retrieved values, so they take less time to decode and take less memory
 * because previous instance is reused.
 */
class Cache {
    private val cachePerDbPerKey: MutableMap<UInt, MutableMap<Key<*>, ReferenceCache>> = mutableMapOf()

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
        val refMap = cachePerDbPerKey.getOrPut(dbIndex) { mutableMapOf() }.getOrPut(key) {
            ReferenceCache(maxSizeBytes = DEFAULT_CACHE_SIZE_BYTES)
        }
        val value = refMap.get(reference)

        return if (value != null && version == value.version) {
            value.value
        } else {
            return valueReader().also { readValue ->
                if (value == null || value.version < version) {
                    refMap.put(reference, CachedValue(version, readValue))
                }
            }
        }
    }

    /** Delete [key] from the table with [dbIndex] */
    fun delete(dbIndex: UInt, key: Key<*>) {
        cachePerDbPerKey.remove(dbIndex)?.remove(key)
    }
}

private const val DEFAULT_CACHE_SIZE_BYTES = 128 * 1024 * 1024
private const val APPROX_ENTRY_SIZE_BYTES = 1024

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
