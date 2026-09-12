package maryk.datastore.remote

import java.net.ServerSocket
import maryk.datastore.memory.InMemoryDataStore
import maryk.sql.conformance.assertSqlConformance
import maryk.sql.conformance.sqlConformanceModels
import kotlin.test.Test

class SqlConformanceTest {
    @Test
    fun sqlReadContractOverHttp() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = sqlConformanceModels, keepAllVersions = true)
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        try {
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            try { assertSqlConformance(remote) } finally { remote.close() }
        } finally {
            server.stop(0, 1_000)
            store.close()
        }
    }
}
