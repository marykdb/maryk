package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.FloatingFilterPlanRecord
import maryk.datastore.test.assertFloatingFilterPlanEquivalence
import kotlin.test.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

class FloatingFilterPlanTest {
    @Test
    fun rawFloatingEqualityDoesNotDependOnTheExecutionPlan() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", "floating-filter-plans-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to FloatingFilterPlanRecord), keepAllVersions = true)
        try { assertFloatingFilterPlanEquivalence(store) } finally { store.close() }
    }
}
