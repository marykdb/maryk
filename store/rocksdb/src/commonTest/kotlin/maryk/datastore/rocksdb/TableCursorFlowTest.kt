package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.TableCursorFlowRecord
import maryk.datastore.test.assertTableCursorFlow
import kotlin.test.Test
import maryk.createTestDBFolder
import maryk.deleteFolder

class TableCursorFlowTest {
    @Test
    fun livePagesRetainTheirCursorBoundary() = runTest() {
        for (history in listOf(false, true)) for (descending in listOf(false, true))
            for (projected in listOf(false, true)) for (limit in listOf(1u, 2u)) {
            val folder = createTestDBFolder("table-cursor-flow-$history-$descending-$projected-$limit")
            val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to TableCursorFlowRecord), keepAllVersions = history)
            try {
                assertTableCursorFlow(store, descending, projected, limit)
            } finally {
                store.close()
                deleteFolder(folder)
            }
        }
    }
}
