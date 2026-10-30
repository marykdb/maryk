package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.CollectionPresenceRecord
import maryk.datastore.test.assertComplexCollectionComparisons
import maryk.datastore.test.assertEmptyCollectionPresence
import kotlin.test.Test

class EmptyCollectionPresenceTest {
    @Test
    fun exactExistsDistinguishesAbsentEmptyNonemptyAndDeletedContainers() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to CollectionPresenceRecord), keepAllVersions = true)
        try {
            assertEmptyCollectionPresence(store)
        } finally {
            store.close()
        }
    }
    @Test
    fun exactComplexComparisonsDecodeTheirSelectedStructure() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to CollectionPresenceRecord), keepAllVersions = true)
        try {
            assertComplexCollectionComparisons(store)
        } finally {
            store.close()
        }
    }
}
