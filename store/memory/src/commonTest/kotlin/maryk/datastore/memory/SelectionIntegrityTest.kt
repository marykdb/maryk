package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.assertSelectionIntegrity
import maryk.test.models.SelectionIntegrityModel
import kotlin.test.Test

class SelectionIntegrityTest {
    @Test
    fun currentHistoricAndLiveSelectionsRespectEveryBranch() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SelectionIntegrityModel), keepAllVersions = true)
        try { assertSelectionIntegrity(store) } finally { store.close() }
    }
}
