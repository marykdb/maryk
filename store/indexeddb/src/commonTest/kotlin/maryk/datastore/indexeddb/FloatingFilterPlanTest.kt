package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.FloatingFilterPlanRecord
import maryk.datastore.test.assertFloatingFilterPlanEquivalence
import kotlin.test.Test
import kotlin.random.Random

class FloatingFilterPlanTest {
    @Test
    fun rawFloatingEqualityDoesNotDependOnTheExecutionPlan() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("floating-filter-plans-${Random.nextInt()}", mapOf(1u to FloatingFilterPlanRecord), keepAllVersions = true)
        try { assertFloatingFilterPlanEquivalence(store, includeDistinctNaNPayloads = supportsDistinctNaNPayloadsForTests()) } finally { store.close() }
    }
}
