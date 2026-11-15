package maryk.datastore.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.ScanRequest
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Cache to store previously retrieved values, so they take less time to decode and take less memory
 * because previous instance is reused. Byte limits use conservative value estimates,
 * not exact runtime heap sizes. Oversized and unknown/composite values bypass caching.
 */
class Cache(
    private val maxKeysPerDb: Int = DEFAULT_MAX_KEYS_PER_DB,
    private val maxSizeBytesPerKey: Int = DEFAULT_CACHE_SIZE_BYTES_PER_KEY
) {
    private var cachePerDb: HashMap<UInt, DbCache>? = null
    private var requestValues: RequestValueCache? = null
    private var requestMaxBytes: Int? = null

    companion object {
        /** A shared estimated byte budget across models/keys, owned by one request. Zero disables value caching. */
        fun forRequest(maxSizeBytes: Int = 2 * 1024 * 1024): Cache = Cache().apply {
            require(maxSizeBytes >= 0) { "maxSizeBytes cannot be negative" }
            requestMaxBytes = maxSizeBytes
        }

        /** Scalar Get/Scan paths materialize each field once; repeated Get rows use record reuse. */
        fun forRequest(request: IsStoreRequest<*, *>): Cache {
            // One record is decoded in one forward pass, including complex values.
            if (request is GetRequest<*> && request.keys.size <= 1) return forRequest(0)
            val singlePass = request is ScanRequest<*> ||
                (request is GetRequest<*> && request.keys.size <= RequestRecordCache.MAX_KEYS)
            val selected = when (request) {
                is GetRequest<*> -> request.select?.properties
                is ScanRequest<*> -> request.select?.properties
                else -> null
            }
            val knownScalars = singlePass && if (selected == null) request.dataModel.all { knownScalar(it.definition) }
                else selected.all { it is IsDefinitionWrapper<*, *, *, *> && knownScalar(it.definition) }
            return forRequest(if (knownScalars) 0 else 2 * 1024 * 1024)
        }

        private fun knownScalar(definition: Any): Boolean = when (definition) {
            is StringDefinition, is NumberDefinition<*>, is BooleanDefinition,
            is DateDefinition, is DateTimeDefinition, is TimeDefinition,
            is EnumDefinition<*>, is ReferenceDefinition<*>,
            is FixedBytesDefinition, is FlexBytesDefinition -> true
            else -> false
        }
    }

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
        requestMaxBytes?.let { maxBytes ->
            if (maxBytes == 0) return valueReader()
            val values = requestValues ?: RequestValueCache(maxBytes).also { requestValues = it }
            return values.readValue(dbIndex, key, reference, version, valueReader)
        }
        val caches = cachePerDb ?: HashMap<UInt, DbCache>(4).also { cachePerDb = it }
        val dbCache = caches.getOrPut(dbIndex) {
            DbCache(maxKeys = maxKeysPerDb, maxSizeBytesPerKey = maxSizeBytesPerKey)
        }
        val value = dbCache.get(key)?.get(reference)

        return if (value != null && version == value.version) {
            value.value
        } else {
            valueReader().also { readValue ->
                if (value == null || value.version < version) {
                    val size = estimatedCacheValueBytes(readValue)
                    if (size >= 0 && size <= maxSizeBytesPerKey) {
                        dbCache.getOrCreate(key).put(reference, CacheEntry(version, readValue, size.toInt()))
                    } else {
                        // A newer uncacheable value must not retain its stale predecessor.
                        dbCache.removeReference(key, reference)
                    }
                }
            }
        }
    }

    /** Delete [key] from the table with [dbIndex] */
    fun delete(dbIndex: UInt, key: Key<*>) {
        if (requestMaxBytes != null) {
            requestValues?.delete(dbIndex, key)
            return
        }
        val caches = cachePerDb ?: return
        val dbCache = caches[dbIndex] ?: return
        dbCache.delete(key)
        if (dbCache.isEmpty()) {
            caches.remove(dbIndex)
        }
    }
}

private const val DEFAULT_MAX_KEYS_PER_DB = 1024
private const val DEFAULT_CACHE_SIZE_BYTES_PER_KEY = 128 * 1024
private const val APPROX_ENTRY_SIZE_BYTES = 1024

// Includes an allowance for entry/LRU metadata and the value object's header. Keep
// the existing entry cap as a second bound. Sizing is O(1), without copying Bytes
// or traversing possibly cyclic/mutable user objects. Long arithmetic avoids overflow.
private const val ENTRY_OVERHEAD_BYTES = 128L

internal fun estimatedCacheValueBytes(value: Any?): Long = when (value) {
    is String -> ENTRY_OVERHEAD_BYTES + 2L * value.length
    is Bytes -> ENTRY_OVERHEAD_BYTES + value.size
    is ByteArray -> ENTRY_OVERHEAD_BYTES + value.size
    is BooleanArray -> ENTRY_OVERHEAD_BYTES + value.size
    is CharArray -> ENTRY_OVERHEAD_BYTES + 2L * value.size
    is ShortArray -> ENTRY_OVERHEAD_BYTES + 2L * value.size
    is IntArray -> ENTRY_OVERHEAD_BYTES + 4L * value.size
    is FloatArray -> ENTRY_OVERHEAD_BYTES + 4L * value.size
    is LongArray -> ENTRY_OVERHEAD_BYTES + 8L * value.size
    is DoubleArray -> ENTRY_OVERHEAD_BYTES + 8L * value.size
    null, Unit, is Boolean, is Char, is Byte, is Short, is Int, is Long,
    is Float, is Double, is UByte, is UShort, is UInt, is ULong,
    is Enum<*>, is IndexedEnum, is LocalDate, is LocalTime, is LocalDateTime,
    is Instant, is Duration, is Uuid -> ENTRY_OVERHEAD_BYTES
    else -> -1L
}

private class CacheEntry(val version: ULong, val value: Any?, val sizeBytes: Int)

private class DbCache(
    private val maxKeys: Int,
    private val maxSizeBytesPerKey: Int
) {
    private val values = CacheLruMap<Key<*>, ReferenceCache>(maxKeys.coerceAtMost(16))

    fun get(key: Key<*>): ReferenceCache? {
        return values.get(key)
    }

    fun getOrCreate(key: Key<*>): ReferenceCache {
        values.get(key)?.let { return it }
        val cache = ReferenceCache(maxSizeBytes = maxSizeBytesPerKey)
        values.put(key, cache)

        if (values.size > maxKeys) {
            values.removeOldest()
        }

        return cache
    }

    fun delete(key: Key<*>) {
        values.remove(key)
    }

    fun removeReference(key: Key<*>, reference: IsPropertyReferenceForCache<*, *>) {
        val cache = values.get(key) ?: return
        cache.remove(reference)
        if (cache.isEmpty()) delete(key)
    }

    fun isEmpty() = values.isEmpty()
}

private class ReferenceCache(private val maxSizeBytes: Int) {
    private val maxEntries = max(1, maxSizeBytes / APPROX_ENTRY_SIZE_BYTES)
    private val values = CacheLruMap<IsPropertyReferenceForCache<*, *>, CacheEntry>(maxEntries.coerceAtMost(16))
    private var sizeBytes = 0L

    fun get(reference: IsPropertyReferenceForCache<*, *>): CacheEntry? {
        return values.get(reference)
    }

    fun put(reference: IsPropertyReferenceForCache<*, *>, value: CacheEntry) {
        val previous = values.put(reference, value)
        if (previous != null) {
            sizeBytes -= previous.sizeBytes
        }
        sizeBytes += value.sizeBytes

        while (values.size > maxEntries || sizeBytes > maxSizeBytes) {
            sizeBytes -= values.removeOldest()!!.sizeBytes
        }
    }

    fun remove(reference: IsPropertyReferenceForCache<*, *>) {
        val removed = values.remove(reference) ?: return
        sizeBytes -= removed.sizeBytes
    }

    fun isEmpty() = values.isEmpty()
}

/** Reuses linked nodes on hits instead of allocating LinkedHashSet entries on every touch. */
internal class CacheLruMap<K : Any, V : Any>(initialCapacity: Int) {
    private class Node<K, V>(val key: K, var value: V) {
        var previous: Node<K, V>? = null
        var next: Node<K, V>? = null
    }
    private val nodes = HashMap<K, Node<K, V>>(initialCapacity)
    private var first: Node<K, V>? = null
    private var last: Node<K, V>? = null
    val size: Int get() = nodes.size
    fun isEmpty() = nodes.isEmpty()

    fun get(key: K): V? {
        val node = nodes[key] ?: return null
        touch(node)
        return node.value
    }

    fun put(key: K, value: V): V? {
        val existing = nodes[key]
        if (existing != null) {
            val previous = existing.value
            existing.value = value
            touch(existing)
            return previous
        }
        val node = Node(key, value)
        nodes[key] = node
        append(node)
        return null
    }

    fun remove(key: K): V? {
        val node = nodes.remove(key) ?: return null
        unlink(node)
        return node.value
    }

    fun removeOldest(): V? = first?.let { remove(it.key) }

    private fun touch(node: Node<K, V>) {
        if (node === last) return
        unlink(node)
        append(node)
    }

    private fun append(node: Node<K, V>) {
        node.previous = last
        last?.next = node
        if (first == null) first = node
        last = node
    }

    private fun unlink(node: Node<K, V>) {
        val previous = node.previous
        val next = node.next
        if (previous == null) first = next else previous.next = next
        if (next == null) last = previous else next.previous = previous
        node.previous = null
        node.next = null
    }
}
