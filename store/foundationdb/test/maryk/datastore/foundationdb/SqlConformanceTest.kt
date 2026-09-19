package maryk.datastore.foundationdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import maryk.sql.conformance.assertSqlConformance
import maryk.sql.conformance.sqlConformanceModels
import kotlin.test.Test
import kotlin.uuid.Uuid

class SqlConformanceTest {
    @Test
    fun sqlReadContract() = runTest {
        withContext(Dispatchers.Default) {
            val store = FoundationDBDataStore.open(
                directoryPath = listOf("maryk", "test", "sql-conformance", Uuid.random().toString()),
                dataModelsById = sqlConformanceModels,
                keepAllVersions = true,
            )
            try { assertSqlConformance(store) } finally { store.close() }
        }
    }
}
