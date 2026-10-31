package maryk.datastore.foundationdb

import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import maryk.datastore.shared.IsDataStore
import maryk.datastore.test.StatefulIntegrityRecord
import maryk.datastore.test.assertCollectionBoundaryIntegrity

class CollectionBoundaryIntegrityTest {
    private suspend fun withStore(block: suspend (IsDataStore) -> Unit) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "collection-boundary-integrity-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to StatefulIntegrityRecord), keepAllVersions = true,
        )
        try {
            block(store)
        } finally {
            store.close()
        }
    }

    @Test
    fun largeCollectionsRetainCurrentAndHistoricValues() = runTest(timeout = 3.minutes) {
        withStore { assertCollectionBoundaryIntegrity(it) }
    }
}
