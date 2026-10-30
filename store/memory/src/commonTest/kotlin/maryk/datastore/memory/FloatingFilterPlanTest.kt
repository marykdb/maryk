package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.FloatingFilterPlanRecord
import maryk.datastore.test.assertFloatingFilterPlanEquivalence
import kotlin.test.Test

class FloatingFilterPlanTest {
    @Test
    fun rawFloatingEqualityDoesNotDependOnTheExecutionPlan() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to FloatingFilterPlanRecord), keepAllVersions = true)
        try { assertFloatingFilterPlanEquivalence(store) } finally { store.close() }
    }
}
