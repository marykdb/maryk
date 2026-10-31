package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.LiveReferencedRecord
import maryk.datastore.test.LiveGeoFilter
import maryk.datastore.test.assertGeoParentFilterChanges
import maryk.datastore.test.LiveSearchFilter
import maryk.datastore.test.assertNamedFilterChanges
import maryk.datastore.test.assertReferencedFilterAddition
import kotlin.test.Test

class LiveFilterMembershipTest {
    @Test
    fun changesRespectGeoParentMembership() = runTest {
        for (kind in LiveGeoFilter.entries) for (getQuery in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to LiveReferencedRecord))
            try {
                assertGeoParentFilterChanges(store, kind, getQuery)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun changesRespectNamedFilters() = runTest {
        for (filter in LiveSearchFilter.entries) for (negated in listOf(false, true)) for (getQuery in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to LiveReferencedRecord))
            try {
                assertNamedFilterChanges(store, filter, negated, getQuery)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun additionsRespectReferencedFilters() = runTest {
        for (getQuery in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to LiveReferencedRecord))
            try {
                assertReferencedFilterAddition(store, getQuery)
            } finally {
                store.close()
            }
        }
    }
}
