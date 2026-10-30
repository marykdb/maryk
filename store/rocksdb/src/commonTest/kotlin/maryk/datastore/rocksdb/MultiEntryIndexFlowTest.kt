package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.assertMultiEntryIndexFlowRefill
import maryk.datastore.test.assertScanUpdatesOldRecordRefill
import maryk.datastore.test.assertFrozenIndexFlowBoundary
import maryk.datastore.test.IndexFlowBoundaryOrigin
import maryk.test.models.AnyValueMapIndexModel
import kotlin.test.Test
import maryk.createTestDBFolder
import maryk.deleteFolder

class MultiEntryIndexFlowTest {
    @Test
    fun refillPreservesDistinctRowsAndTheirOrder() = runTest() {
        for (descending in listOf(false, true)) {
            val folder = createTestDBFolder("multi-entry-flow-$descending")
            val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true)
            try {
                assertMultiEntryIndexFlowRefill(store, descending)
            } finally {
                store.close()
                deleteFolder(folder)
            }
        }
    }
    @Test
    fun refillRetainsFrozenStartAndCursorBoundaries() = runTest() {
        for (descending in listOf(false, true)) for (origin in IndexFlowBoundaryOrigin.entries) for (limit in listOf(1u, 2u)) {
            val folder = createTestDBFolder("frozen-index-flow-$descending-$origin-$limit")
            val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true)
            try {
                assertFrozenIndexFlowBoundary(store, descending, origin, limit)
            } finally {
                store.close()
                deleteFolder(folder)
            }
        }
    }
    @Test
    fun scanUpdatesRefillsWithRowsOlderThanFromVersion() = runTest() {
        for (descending in listOf(false, true)) {
            val folder = createTestDBFolder("scan-updates-old-refill-$descending")
            val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true)
            try {
                assertScanUpdatesOldRecordRefill(store, descending)
            } finally {
                store.close()
                deleteFolder(folder)
            }
        }
    }
}
