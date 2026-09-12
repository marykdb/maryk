package maryk.datastore.rocksdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.sql.conformance.assertSqlConformance
import maryk.sql.conformance.sqlConformanceModels
import kotlin.test.Test

class SqlConformanceTest {
    @Test
    fun sqlReadContract() = runTest {
        withContext(Dispatchers.Default) {
            val folder = createTestDBFolder("sql-conformance")
            val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = sqlConformanceModels, keepAllVersions = true)
            try { assertSqlConformance(store) } finally { store.close(); deleteFolder(folder) }
        }
    }
}
