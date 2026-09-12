package maryk.datastore.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import maryk.sql.conformance.assertSqlConformance
import maryk.sql.conformance.sqlConformanceModels
import kotlin.test.Test

class SqlConformanceTest {
    @Test
    fun sqlReadContract() = runTest {
        withContext(Dispatchers.Default) {
            val store = InMemoryDataStore.open(dataModelsById = sqlConformanceModels, keepAllVersions = true)
            try { assertSqlConformance(store) } finally { store.close() }
        }
    }
}
