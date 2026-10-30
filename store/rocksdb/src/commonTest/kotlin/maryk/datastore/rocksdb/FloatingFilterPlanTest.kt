package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.FloatingFilterPlanRecord
import maryk.datastore.test.assertFloatingFilterPlanEquivalence
import kotlin.test.Test
import maryk.createTestDBFolder
import maryk.deleteFolder

class FloatingFilterPlanTest {
    @Test
    fun rawFloatingEqualityDoesNotDependOnTheExecutionPlan() = runTest {
        val folder = createTestDBFolder("floating-filter-plans")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to FloatingFilterPlanRecord), keepAllVersions = true)
        try { assertFloatingFilterPlanEquivalence(store) } finally { store.close(); deleteFolder(folder) }
    }
}
