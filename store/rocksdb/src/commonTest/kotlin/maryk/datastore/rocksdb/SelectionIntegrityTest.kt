package maryk.datastore.rocksdb

import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SelectionIntegrityModel
import maryk.datastore.test.assertSelectionIntegrity
import maryk.deleteFolder

class SelectionIntegrityTest {
    private suspend fun withStore(block: suspend (IsDataStore) -> Unit) {
        val folder = createTestDBFolder("selection-integrity")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to SelectionIntegrityModel), keepAllVersions = true)
        try {
            block(store)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun currentHistoricAndLiveSelectionsRespectEveryBranch() = runTest(timeout = 3.minutes) {
        withStore { assertSelectionIntegrity(it) }
    }
}
