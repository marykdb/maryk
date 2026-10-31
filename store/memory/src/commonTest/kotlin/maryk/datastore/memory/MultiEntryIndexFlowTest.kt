package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.assertMultiEntryIndexFlowRefill
import maryk.datastore.test.assertScanUpdatesOldRecordRefill
import maryk.datastore.test.assertFrozenIndexFlowBoundary
import maryk.datastore.test.assertIndexCursorFlowMembership
import maryk.datastore.test.IndexFlowBoundaryOrigin
import maryk.datastore.test.IndexCursorFlowChange
import maryk.test.models.AnyValueMapIndexModel
import kotlin.test.Test

class MultiEntryIndexFlowTest {
    @Test
    fun incrementalChangesRetainCursorMembership() = runTest {
        for (descending in listOf(false, true)) for (scenario in IndexCursorFlowChange.entries) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true)
            try {
                assertIndexCursorFlowMembership(store, descending, scenario)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun refillPreservesDistinctRowsAndTheirOrder() = runTest() {
        for (descending in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true)
            try {
                assertMultiEntryIndexFlowRefill(store, descending)
            } finally {
                store.close()
            }
        }
    }
    @Test
    fun refillRetainsFrozenStartAndCursorBoundaries() = runTest() {
        for (descending in listOf(false, true)) for (origin in IndexFlowBoundaryOrigin.entries) for (limit in listOf(1u, 2u)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true)
            try {
                assertFrozenIndexFlowBoundary(store, descending, origin, limit)
            } finally {
                store.close()
            }
        }
    }
    @Test
    fun scanUpdatesRefillsWithRowsOlderThanFromVersion() = runTest() {
        for (descending in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to AnyValueMapIndexModel), keepAllVersions = true)
            try {
                assertScanUpdatesOldRecordRefill(store, descending)
            } finally {
                store.close()
            }
        }
    }
}
