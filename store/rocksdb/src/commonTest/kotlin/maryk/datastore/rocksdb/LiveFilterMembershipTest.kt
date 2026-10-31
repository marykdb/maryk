package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.datastore.shared.IsDataStore
import maryk.datastore.test.LiveReferencedRecord
import maryk.datastore.test.LiveGeoFilter
import maryk.datastore.test.assertGeoParentFilterChanges
import maryk.datastore.test.LiveSearchFilter
import maryk.datastore.test.assertNamedFilterChanges
import maryk.datastore.test.assertReferencedFilterAddition
import kotlin.test.Test

class LiveFilterMembershipTest {
    private suspend fun withStore(block: suspend (IsDataStore) -> Unit) {
        val folder = createTestDBFolder("live-filter-membership")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to LiveReferencedRecord))
        try {
            block(store)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun changesRespectGeoParentMembership() = runTest {
        for (kind in LiveGeoFilter.entries) for (getQuery in listOf(false, true)) {
            withStore { assertGeoParentFilterChanges(it, kind, getQuery) }
        }
    }

    @Test
    fun additionsRespectReferencedFilters() = runTest {
        for (getQuery in listOf(false, true)) withStore { assertReferencedFilterAddition(it, getQuery) }
    }

    @Test
    fun changesRespectNamedFilters() = runTest {
        for (filter in LiveSearchFilter.entries) for (negated in listOf(false, true)) for (getQuery in listOf(false, true)) {
            withStore { assertNamedFilterChanges(it, filter, negated, getQuery) }
        }
    }
}
