package maryk.datastore.remote

import java.net.ServerSocket
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.AtomicDataStore
import maryk.datastore.shared.TransactionalDataStore
import maryk.datastore.test.ModelContractRegressionCases
import maryk.datastore.test.modelContractModels
import kotlin.test.Test

class ModelContractRegressionTest {
    private val cases = ModelContractRegressionCases {
        val store = InMemoryDataStore.open(
            keepAllVersions = true, keepUpdateHistoryIndex = true, dataModelsById = modelContractModels,
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
    fun collectionChecksUseCompleteValuesAndRejectMissingProperties() = runBoundedIntegrationTest {
        cases.collectionChecksUseCompleteValuesAndRejectMissingProperties()
    }

    @Test
    fun variableLengthBoundariesKeepExactUpperAndStrictLowerSemantics() = runBoundedIntegrationTest {
        cases.variableLengthBoundariesKeepExactUpperAndStrictLowerSemantics()
    }

    @Test
    fun inclusiveCompositeDateBoundaryPagesInBothDirections() = runBoundedIntegrationTest {
        cases.inclusiveCompositeDateBoundaryPagesInBothDirections()
    }

    @Test
    fun collectionReplacementSurvivesTransactionAndAtomicTransport() = runBoundedIntegrationTest {
        cases.collectionReplacementSurvivesTransactionAndAtomicTransport()
    }
}
