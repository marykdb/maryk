package maryk.datastore.foundationdb

import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SelectionIntegrityModel
import maryk.datastore.test.assertSelectionIntegrity

class SelectionIntegrityTest {
    private suspend fun withStore(block: suspend (IsDataStore) -> Unit) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "selection-integrity-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to SelectionIntegrityModel), keepAllVersions = true,
        )
        try {
            block(store)
        } finally {
            store.close()
        }
    }

    @Test
    fun currentHistoricAndLiveSelectionsRespectEveryBranch() = runTest(timeout = 3.minutes) {
        withStore { assertSelectionIntegrity(it) }
    }
}
