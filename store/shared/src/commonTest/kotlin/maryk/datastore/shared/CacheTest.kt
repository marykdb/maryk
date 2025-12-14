package maryk.datastore.shared

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import kotlin.test.Test
import kotlin.test.assertEquals

internal class CacheTest {
    @Test
    fun deleteOnlyRemovesGivenKey() {
        val cache = Cache()
        val dbIndex = 1u

        val keyToDelete = Key<IsValuesDataModel>(byteArrayOf(1))
        val keyToKeep = Key<IsValuesDataModel>(byteArrayOf(2))
        val reference = object : IsPropertyReferenceForCache<String, StringDefinition> {
            override val propertyDefinition = StringDefinition(required = false)
        }

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
}

