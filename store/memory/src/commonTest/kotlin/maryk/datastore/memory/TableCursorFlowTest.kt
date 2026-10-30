package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.TableCursorFlowRecord
import maryk.datastore.test.assertTableCursorFlow
import kotlin.test.Test

class TableCursorFlowTest {
    @Test
    fun livePagesRetainTheirCursorBoundary() = runTest() {
        for (history in listOf(false, true)) for (descending in listOf(false, true))
            for (projected in listOf(false, true)) for (limit in listOf(1u, 2u)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to TableCursorFlowRecord), keepAllVersions = history)
            try {
                assertTableCursorFlow(store, descending, projected, limit)
            } finally {
                store.close()
            }
        }
    }
}
