package maryk.datastore.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.properties.types.Bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class CacheTest {
    @Test
    fun deleteOnlyRemovesGivenKey() {
        val cache = Cache()
        val dbIndex = 1u

        val keyToDelete = Key<IsValuesDataModel>(byteArrayOf(1))
        val keyToKeep = Key<IsValuesDataModel>(byteArrayOf(2))
        val reference = testReference()

        var deleteKeyReads = 0
        var keepKeyReads = 0

        cache.readValue(
            dbIndex = dbIndex,
            key = keyToDelete,
            reference = reference,
            version = 1uL,
        ) {
            deleteKeyReads++
            "delete"
        }

        cache.readValue(
            dbIndex = dbIndex,
            key = keyToKeep,
            reference = reference,
            version = 1uL,
        ) {
            keepKeyReads++
            "keep"
        }

        cache.delete(dbIndex, keyToDelete)

        cache.readValue(
            dbIndex = dbIndex,
            key = keyToKeep,
            reference = reference,
            version = 1uL,
        ) {
            keepKeyReads++
            "keep2"
        }

        assertEquals(1, deleteKeyReads)
        assertEquals(1, keepKeyReads)
    }

    @Test
    fun evictsOldestKeyWhenDbCacheIsFull() {
        val cache = Cache(maxKeysPerDb = 1)
        val dbIndex = 1u
        val firstKey = Key<IsValuesDataModel>(byteArrayOf(1))
        val secondKey = Key<IsValuesDataModel>(byteArrayOf(2))
        val reference = testReference()

        var firstKeyReads = 0
        var secondKeyReads = 0

        cache.readValue(dbIndex, firstKey, reference, 1uL) {
            firstKeyReads++
            "first"
        }
        cache.readValue(dbIndex, secondKey, reference, 1uL) {
            secondKeyReads++
            "second"
        }
        cache.readValue(dbIndex, firstKey, reference, 1uL) {
            firstKeyReads++
            "first2"
        }
        cache.readValue(dbIndex, secondKey, reference, 1uL) {
            secondKeyReads++
            "second2"
        }

        assertEquals(2, firstKeyReads)
        assertEquals(2, secondKeyReads)
    }

    @Test
    fun failedReadDoesNotCreateEntryOrEvictExistingKey() {
        val cache = Cache(maxKeysPerDb = 1)
        val dbIndex = 1u
        val cachedKey = Key<IsValuesDataModel>(byteArrayOf(1))
        val failedKey = Key<IsValuesDataModel>(byteArrayOf(2))
        val reference = testReference()

        var cachedReads = 0

        cache.readValue(dbIndex, cachedKey, reference, 1uL) {
            cachedReads++
            "cached"
        }

        assertFailsWith<IllegalStateException> {
            cache.readValue(dbIndex, failedKey, reference, 1uL) {
                throw IllegalStateException("read failed")
            }
        }

        cache.readValue(dbIndex, cachedKey, reference, 1uL) {
            cachedReads++
            "cached2"
        }

        assertEquals(1, cachedReads)
    }

    @Test
    fun largeValuesEvictLeastRecentlyUsedReferences() {
        val cache = Cache(maxSizeBytesPerKey = 4096)
        val key = Key<IsValuesDataModel>(byteArrayOf(1))
        val references = List(3) { testReference() }
        val reads = IntArray(3)
        fun read(index: Int) = cache.readValue(1u, key, references[index], 1uL) {
            reads[index]++
            "x".repeat(700)
        }
        read(0); read(1); read(0); read(2); read(0)
        assertEquals(listOf(1, 1, 1), reads.toList())
        read(1)
        assertEquals(2, reads[1])
    }

    @Test
    fun oversizedAndUnknownValuesDoNotEvictOtherKeys() {
        val cache = Cache(maxKeysPerDb = 1, maxSizeBytesPerKey = 4096)
        val cachedKey = Key<IsValuesDataModel>(byteArrayOf(1))
        val otherKey = Key<IsValuesDataModel>(byteArrayOf(2))
        val reference = testReference()
        var reads = 0
        fun readSmall() = cache.readValue(1u, cachedKey, reference, 1uL) { reads++; "small" }
        readSmall()
        repeat(2) {
            assertEquals(8192, (cache.readValue(1u, otherKey, reference, 1uL) { ByteArray(8192) } as ByteArray).size)
            val cyclic = mutableListOf<Any>()
            cyclic.add(cyclic)
            cache.readValue(1u, otherKey, reference, 1uL) { cyclic }
        }
        readSmall()
        assertEquals(1, reads)
    }

    @Test
    fun oversizedValuesAreReturnedButNeverRetained() {
        val cache = Cache(maxSizeBytesPerKey = 1024)
        val key = Key<IsValuesDataModel>(byteArrayOf(1))
        val reference = testReference()
        var reads = 0
        repeat(2) {
            assertEquals("x".repeat(1024), cache.readValue(1u, key, reference, 1uL) { reads++; "x".repeat(1024) })
        }
        assertEquals(2, reads)
    }

    @Test
    fun replacementSubtractsOldWeightAndEvictsAsManyEntriesAsNeeded() {
        val cache = Cache(maxSizeBytesPerKey = 4096)
        val key = Key<IsValuesDataModel>(byteArrayOf(1))
        val references = List(3) { testReference() }
        references.forEach { cache.readValue(1u, key, it, 1uL) { "x".repeat(400) } }
        cache.readValue(1u, key, references[0], 2uL) { "x".repeat(1600) }
        cache.readValue(1u, key, references[0], 3uL) { "small" }
        cache.readValue(1u, key, references[1], 2uL) { "y".repeat(1000) }
        assertEquals("small", cache.readValue(1u, key, references[0], 3uL) { error("Replacement weight was not released") })
        assertEquals("y".repeat(1000), cache.readValue(1u, key, references[1], 2uL) { error("Unexpected eviction") })
        assertEquals("miss", cache.readValue(1u, key, references[2], 1uL) { "miss" })
    }

    @Test
    fun newerUncacheableValueDropsStaleEntryButOlderReadsPreserveNewerEntry() {
        val cache = Cache(maxSizeBytesPerKey = 1024)
        val key = Key<IsValuesDataModel>(byteArrayOf(1))
        val reference = testReference()
        cache.readValue(1u, key, reference, 2uL) { "new" }
        cache.readValue(1u, key, reference, 1uL) { "x".repeat(2048) }
        assertEquals("new", cache.readValue(1u, key, reference, 2uL) { error("Older read evicted newer version") })
        cache.readValue(1u, key, reference, 3uL) { ByteArray(2048) }
        assertEquals("reloaded", cache.readValue(1u, key, reference, 2uL) { "reloaded" })
    }

    @Test
    fun byteWrappersAndArraysUseTheirPayloadSize() {
        for (value in listOf(Bytes(ByteArray(2048)), ByteArray(2048), LongArray(256))) {
            val cache = Cache(maxSizeBytesPerKey = 4096)
            val key = Key<IsValuesDataModel>(byteArrayOf(1))
            val references = List(2) { testReference() }
            cache.readValue(1u, key, references[0], 1uL) { value }
            cache.readValue(1u, key, references[1], 1uL) { value }
            assertEquals("evicted", cache.readValue(1u, key, references[0], 1uL) { "evicted" })
        }
    }

    @Test
    fun nullValuesAreCachedAndTinyBudgetsBypassSafely() {
        val key = Key<IsValuesDataModel>(byteArrayOf(1))
        val reference = testReference()
        val normal = Cache()
        normal.readValue(1u, key, reference, 1uL) { null }
        assertEquals(null, normal.readValue(1u, key, reference, 1uL) { error("Null was not cached") })
        val tiny = Cache(maxSizeBytesPerKey = 1)
        var reads = 0
        repeat(2) { tiny.readValue(1u, key, reference, 1uL) { reads++; 1 } }
        assertEquals(2, reads)
    }

    @Test
    fun smallValuesRetainTheExistingEntryCountSafeguard() {
        val cache = Cache()
        val key = Key<IsValuesDataModel>(byteArrayOf(1))
        val references = List(129) { testReference() }
        references.forEach { cache.readValue(1u, key, it, 1uL) { "small" } }
        assertEquals("small", cache.readValue(1u, key, references[128], 1uL) { error("Recent reference evicted") })
        assertEquals("evicted", cache.readValue(1u, key, references[0], 1uL) { "evicted" })
    }

    @Test
    fun keyTouchesAndDeletingEntriesPreserveRemainingEntries() {
        val cache = Cache(maxKeysPerDb = 3)
        val keys = List(5) { Key<IsValuesDataModel>(byteArrayOf(it.toByte())) }
        val reference = testReference()
        for (index in 0..2) cache.readValue(1u, keys[index], reference, 1uL) { index }
        fun hit(index: Int) = assertEquals(index, cache.readValue(1u, keys[index], reference, 1uL) { error("Lost key $index") })
        hit(1); hit(0); hit(0)
        cache.readValue(1u, keys[3], reference, 1uL) { 3 }
        hit(1); hit(0); hit(3)
        assertEquals("evicted", cache.readValue(1u, keys[2], reference, 1uL) { "evicted" })
        // Remove the remaining head, tail, and finally the only key, then reuse the cache.
        cache.delete(1u, keys[0])
        cache.delete(1u, keys[2])
        hit(3)
        cache.delete(1u, keys[3])
        cache.readValue(1u, keys[4], reference, 1uL) { 4 }
        hit(4); hit(4)
    }

    @Test
    fun knownScalarAndSmallArrayValuesRemainCacheable() {
        val key = Key<IsValuesDataModel>(byteArrayOf(1))
        for (value in listOf(
            true, 'x', 1.toByte(), 1.toShort(), 1, 1L, 1f, 1.0, 1u, 1uL,
            LocalDate(2026, 1, 1), LocalTime(12, 0), LocalDateTime(2026, 1, 1, 12, 0),
            Instant.fromEpochMilliseconds(1000), Uuid.fromLongs(1, 2),
            Bytes(ByteArray(16)), ByteArray(16), IntArray(4), LongArray(4),
            CharArray(4), BooleanArray(4), ShortArray(4), FloatArray(4), DoubleArray(4),
        )) {
            val cache = Cache()
            val reference = testReference()
            cache.readValue(1u, key, reference, 1uL) { value }
            assertSame(value, cache.readValue(1u, key, reference, 1uL) { error("Known value bypassed cache: $value") })
        }
    }

    private fun testReference() = object : IsPropertyReferenceForCache<String, StringDefinition> {
        override val propertyDefinition = StringDefinition(required = false)
    }
}
