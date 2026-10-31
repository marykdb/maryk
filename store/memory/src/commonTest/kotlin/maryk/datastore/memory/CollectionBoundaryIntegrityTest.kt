package maryk.datastore.memory

import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import maryk.datastore.test.StatefulIntegrityRecord
import maryk.datastore.test.assertCollectionBoundaryIntegrity

class CollectionBoundaryIntegrityTest {
    @Test
    fun largeCollectionsRetainCurrentAndHistoricValues() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to StatefulIntegrityRecord), keepAllVersions = true)
        try { assertCollectionBoundaryIntegrity(store) } finally { store.close() }
    }
}
