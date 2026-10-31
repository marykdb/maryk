package maryk.datastore.indexeddb

import kotlin.random.Random
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SelectionIntegrityModel
import maryk.datastore.test.assertSelectionIntegrity

class SelectionIntegrityTest {
    private suspend fun withStore(block: suspend (IsDataStore) -> Unit) {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open(
            "selection-integrity-${Random.nextInt()}", mapOf(1u to SelectionIntegrityModel), keepAllVersions = true,
        )
        try {
            block(store)
        } finally {
            store.close()
        }
    }

    @Test
    fun currentHistoricAndLiveSelectionsRespectEveryBranch() = runTest {
        withStore { assertSelectionIntegrity(it) }
    }
}
