package maryk.datastore.indexeddb

import kotlin.random.Random
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import maryk.datastore.shared.IsDataStore
import maryk.datastore.test.StatefulIntegrityRecord
import maryk.datastore.test.assertCollectionBoundaryIntegrity

class CollectionBoundaryIntegrityTest {
    private suspend fun withStore(block: suspend (IsDataStore) -> Unit) {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open(
            "collection-boundary-integrity-${Random.nextInt()}", mapOf(1u to StatefulIntegrityRecord), keepAllVersions = true,
        )
        try {
            block(store)
        } finally {
            store.close()
        }
    }

    @Test
    fun largeCollectionsRetainCurrentAndHistoricValues() = runTest {
        withStore { assertCollectionBoundaryIntegrity(it) }
    }
}
