package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.shared.IsDataStore
import maryk.datastore.test.LiveReferencedRecord
import maryk.datastore.test.LiveGeoFilter
import maryk.datastore.test.assertGeoParentFilterChanges
import maryk.datastore.test.LiveSearchFilter
import maryk.datastore.test.assertNamedFilterChanges
import maryk.datastore.test.assertReferencedFilterAddition
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class LiveFilterMembershipTest {
    private suspend fun withStore(block: suspend (IsDataStore) -> Unit) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "live-filter-membership-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to LiveReferencedRecord),
        )
        try {
            block(store)
        } finally {
            store.close()
        }
    }

    @Test
    fun changesRespectGeoParentMembership() = runTest(timeout = 3.minutes) {
        for (kind in LiveGeoFilter.entries) for (getQuery in listOf(false, true)) {
            withStore { assertGeoParentFilterChanges(it, kind, getQuery) }
        }
    }

    @Test
    fun additionsRespectReferencedFilters() = runTest(timeout = 3.minutes) {
        for (getQuery in listOf(false, true)) withStore { assertReferencedFilterAddition(it, getQuery) }
    }

    @Test
    fun changesRespectNamedFilters() = runTest(timeout = 3.minutes) {
        for (filter in LiveSearchFilter.entries) for (negated in listOf(false, true)) for (getQuery in listOf(false, true)) {
            withStore { assertNamedFilterChanges(it, filter, negated, getQuery) }
        }
    }
}
