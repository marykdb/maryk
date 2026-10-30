package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.assertMultiEntryIndexFlowRefill
import maryk.datastore.test.assertScanUpdatesOldRecordRefill
import maryk.datastore.test.assertFrozenIndexFlowBoundary
import maryk.datastore.test.IndexFlowBoundaryOrigin
import maryk.test.models.AnyValueMapIndexModel
import kotlin.test.Test
import kotlin.random.Random

class MultiEntryIndexFlowTest {
    @Test
    fun refillPreservesDistinctRowsAndTheirOrder() = runTest() {
        installIndexedDbForTests()
        for (descending in listOf(false, true)) {
            val store = IndexedDbDataStore.open(
                "multi-entry-flow-$descending-${Random.nextInt()}",
                mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true,
            )
            try {
                assertMultiEntryIndexFlowRefill(store, descending)
            } finally {
                store.close()
            }
        }
    }
    @Test
    fun refillRetainsFrozenStartAndCursorBoundaries() = runTest() {
        installIndexedDbForTests()
        for (descending in listOf(false, true)) for (origin in IndexFlowBoundaryOrigin.entries) for (limit in listOf(1u, 2u)) {
            val store = IndexedDbDataStore.open(
                "frozen-index-flow-$descending-$origin-$limit-${Random.nextInt()}",
                mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true,
            )
            try {
                assertFrozenIndexFlowBoundary(store, descending, origin, limit)
            } finally {
                store.close()
            }
        }
    }
    @Test
    fun scanUpdatesRefillsWithRowsOlderThanFromVersion() = runTest() {
        installIndexedDbForTests()
        for (descending in listOf(false, true)) {
            val store = IndexedDbDataStore.open(
                "old-row-refill-$descending-${Random.nextInt()}",
                mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true,
            )
            try {
                assertScanUpdatesOldRecordRefill(store, descending)
            } finally {
                store.close()
            }
        }
    }
}
