package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.TableCursorFlowRecord
import maryk.datastore.test.assertTableCursorFlow
import kotlin.test.Test
import kotlin.random.Random

class TableCursorFlowTest {
    @Test
    fun livePagesRetainTheirCursorBoundary() = runTest() {
        installIndexedDbForTests()
        for (history in listOf(false, true)) for (descending in listOf(false, true))
            for (projected in listOf(false, true)) for (limit in listOf(1u, 2u)) {
            val store = IndexedDbDataStore.open(
                "table-cursor-flow-${Random.nextInt()}", mapOf(1u to TableCursorFlowRecord), keepAllVersions = history,
            )
            try {
                assertTableCursorFlow(store, descending, projected, limit)
            } finally {
                store.close()
            }
        }
    }
}
