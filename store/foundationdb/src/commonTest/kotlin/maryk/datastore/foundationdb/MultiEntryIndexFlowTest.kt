package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.assertMultiEntryIndexFlowRefill
import maryk.datastore.test.assertScanUpdatesOldRecordRefill
import maryk.datastore.test.assertFrozenIndexFlowBoundary
import maryk.datastore.test.assertIndexCursorFlowMembership
import maryk.datastore.test.IndexCursorFlowChange
import maryk.datastore.test.IndexFlowBoundaryOrigin
import maryk.test.models.AnyValueMapIndexModel
import kotlin.test.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

class MultiEntryIndexFlowTest {
    @Test
    fun incrementalChangesRetainCursorMembership() = runTest(timeout = 3.minutes) {
        for (descending in listOf(false, true)) for (scenario in IndexCursorFlowChange.entries) {
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "cursor-membership-$descending-$scenario-${Random.nextInt()}"),
                dataModelsById = mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true,
            )
            try {
                assertIndexCursorFlowMembership(store, descending, scenario)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun refillPreservesDistinctRowsAndTheirOrder() = runTest(timeout = 3.minutes) {
        for (descending in listOf(false, true)) {
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "multi-entry-flow-$descending-${Random.nextInt()}"),
                dataModelsById = mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true,
            )
            try {
                assertMultiEntryIndexFlowRefill(store, descending)
            } finally {
                store.close()
            }
        }
    }
    @Test
    fun refillRetainsFrozenStartAndCursorBoundaries() = runTest(timeout = 3.minutes) {
        for (descending in listOf(false, true)) for (origin in IndexFlowBoundaryOrigin.entries) for (limit in listOf(1u, 2u)) {
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "frozen-index-flow-$descending-$origin-$limit-${Random.nextInt()}"),
                dataModelsById = mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true,
            )
            try {
                assertFrozenIndexFlowBoundary(store, descending, origin, limit)
            } finally {
                store.close()
            }
        }
    }
    @Test
    fun scanUpdatesRefillsWithRowsOlderThanFromVersion() = runTest(timeout = 3.minutes) {
        for (descending in listOf(false, true)) {
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "old-row-refill-$descending-${Random.nextInt()}"),
                dataModelsById = mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true,
            )
            try {
                assertScanUpdatesOldRecordRefill(store, descending)
            } finally {
                store.close()
            }
        }
    }
}
