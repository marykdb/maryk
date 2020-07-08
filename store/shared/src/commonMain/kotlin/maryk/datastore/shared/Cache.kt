package maryk.datastore.shared

import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key

/**
 * Cache to store previously retrieved values so they take less time to decode and take less memory
 * because previous instance is reused.
 */
class Cache(
    val cache: MutableMap<UInt, MutableMap<Key<*>, MutableMap<IsPropertyReferenceForCache<*, *>, CachedValue>>> = mutableMapOf()
) {
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
        val refMap = cache.getOrPut(dbIndex) { mutableMapOf() }.getOrPut(key) { mutableMapOf() }
        val value = refMap[reference]

        return if (value != null && version == value.version) {
            value.value
        } else {
            return valueReader().also { readValue ->
                if (value == null || value.version < version) {
                    refMap[reference] = CachedValue(version, readValue)
                }
            }
        }
    }

    /** Delete [key] from the table with [dbIndex] */
    fun delete(dbIndex: UInt, key: Key<*>) {
        cache[dbIndex]?.remove(key)
    }
}
