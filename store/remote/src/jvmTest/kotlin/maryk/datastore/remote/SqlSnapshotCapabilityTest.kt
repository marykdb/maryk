package maryk.datastore.remote

import java.net.ServerSocket
import kotlinx.coroutines.flow.toList
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.IsDataStore
import maryk.sql.MarykSql
import maryk.sql.SqlConsistency
import maryk.sql.SqlOptions
import maryk.sql.SqlReadOptions
import maryk.sql.conformance.sqlConformanceModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SqlSnapshotCapabilityTest {
    @Test fun historicalSupportReflectsTheRemoteBackend() = runBoundedIntegrationTest {
        for (hasProvider in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = sqlConformanceModels, keepAllVersions = true)
            val backend = if (hasProvider) store else object : IsDataStore by store {}
            val port = ServerSocket(0).use { it.localPort }
            val server = RemoteStoreServer(backend).start("127.0.0.1", port, wait = false)
            try {
                val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
                try {
                    val sql = MarykSql.create(remote, options = SqlOptions(allowTableScan = true))
                    assertEquals(hasProvider, remote.supportsSnapshotVersion)
                    assertEquals(hasProvider, sql.capabilities.historicalReads)
                    assertEquals(0, sql.query("SELECT id FROM SqlConformanceItem").rows.size)
                    if (hasProvider) {
                        val execution = sql.prepare("SELECT id FROM SqlConformanceItem").execute(options = SqlReadOptions(SqlConsistency.Snapshot))
                        execution.rows.toList()
                        assertNotNull(execution.awaitCompletion().snapshotVersion)
                    }
                } finally { remote.close() }
            } finally { server.stop(0, 1000); store.close() }
        }
    }
}
