package maryk.datastore.remote

import java.net.ServerSocket
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.AtomicDataStore
import maryk.datastore.shared.TransactionalDataStore
import maryk.datastore.test.HistoricalOwnershipConformance
import maryk.datastore.test.historicalOwnershipModels
import kotlin.test.Test

class HistoricalOwnershipConformanceTest {
    private val cases = HistoricalOwnershipConformance(retainsErasedValues = true) {
        val store = InMemoryDataStore.open(
            keepAllVersions = true, keepUpdateHistoryIndex = true, dataModelsById = historicalOwnershipModels,
        )
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
        object : IsDataStore by remote, TransactionalDataStore by remote, AtomicDataStore by remote {
            override suspend fun close() {
                try { remote.close() } finally {
                    server.stop(500, 500)
                    store.close()
                }
            }
        }
    }

    @Test
    fun reversedRecordHistoriesPreserveUniqueOwnership() = runBoundedIntegrationTest {
        cases.reversedRecordHistoriesPreserveUniqueOwnership()
    }

    @Test
    fun equalVersionTransfersRetainAppliedOrder() = runBoundedIntegrationTest {
        cases.equalVersionTransfersRetainAppliedOrder()
    }

    @Test
    fun hardDeletedKeyReusePreservesHistoricalIncarnations() = runBoundedIntegrationTest {
        cases.hardDeletedKeyReusePreservesHistoricalIncarnations()
    }
}
