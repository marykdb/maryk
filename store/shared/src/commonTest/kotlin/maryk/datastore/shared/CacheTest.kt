package maryk.datastore.shared

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    private fun testReference() = object : IsPropertyReferenceForCache<String, StringDefinition> {
        override val propertyDefinition = StringDefinition(required = false)
    }
}
