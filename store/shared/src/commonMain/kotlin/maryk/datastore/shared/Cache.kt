package maryk.datastore.shared

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key

/**
 * Cache to store previously retrieved values, so they take less time to decode and take less memory
 * because previous instance is reused.
 */
class Cache {
    private val cachePerDbPerKey: MutableMap<UInt, MutableMap<Key<*>, InMemoryKache<IsPropertyReferenceForCache<*, *>, CachedValue>>> = mutableMapOf()

    /**
     * Read value from the cache if it exists and is of same version, or read it from store and cache it if
     * it is not of same version or does not exist in cache
     */
    suspend fun readValue(
        dbIndex: UInt,
        key: Key<*>,
        reference: IsPropertyReferenceForCache<*, *>,
        version: ULong,
        valueReader: () -> Any?
    ): Any? {
        val refMap = cachePerDbPerKey.getOrPut(dbIndex) { mutableMapOf() }.getOrPut(key) {
            InMemoryKache(maxSize = 128 * 1024 * 1024) {  // 128 MB
                strategy = KacheStrategy.LRU
            }
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
