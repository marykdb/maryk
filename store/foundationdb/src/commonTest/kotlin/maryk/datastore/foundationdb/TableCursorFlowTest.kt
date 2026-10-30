package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.TableCursorFlowRecord
import maryk.datastore.test.assertTableCursorFlow
import kotlin.test.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

class TableCursorFlowTest {
    @Test
    fun livePagesRetainTheirCursorBoundary() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) for (descending in listOf(false, true))
            for (projected in listOf(false, true)) for (limit in listOf(1u, 2u)) {
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "table-cursor-flow-${Random.nextInt()}"),
                dataModelsById = mapOf(1u to TableCursorFlowRecord), keepAllVersions = history,
            )
            try {
                assertTableCursorFlow(store, descending, projected, limit)
            } finally {
                store.close()
            }
        }
    }
}
