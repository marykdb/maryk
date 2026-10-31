package maryk.datastore.rocksdb

import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.datastore.shared.IsDataStore
import maryk.datastore.test.StatefulIntegrityRecord
import maryk.datastore.test.assertCollectionBoundaryIntegrity
import maryk.deleteFolder

class CollectionBoundaryIntegrityTest {
    private suspend fun withStore(block: suspend (IsDataStore) -> Unit) {
        val folder = createTestDBFolder("collection-boundary-integrity")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to StatefulIntegrityRecord), keepAllVersions = true)
        try {
            block(store)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun largeCollectionsRetainCurrentAndHistoricValues() = runTest(timeout = 3.minutes) {
        withStore { assertCollectionBoundaryIntegrity(it) }
    }
}
