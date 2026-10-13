package maryk.datastore.remote

import java.net.ServerSocket
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.AtomicDataStore
import maryk.datastore.shared.TransactionalDataStore
import maryk.datastore.test.HistoryBoundsRegressionCases
import maryk.datastore.test.historyBoundsModels
import kotlin.test.Test

class HistoryBoundsRegressionTest {
    private val cases = HistoryBoundsRegressionCases {
        val store = InMemoryDataStore.open(
            keepAllVersions = true, keepUpdateHistoryIndex = true, dataModelsById = historyBoundsModels,
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
    fun atomicRevisionKeepsAllSelectedChangesAndFiltersBeforeLimit() = runBoundedIntegrationTest {
        cases.atomicRevisionKeepsAllSelectedChangesAndFiltersBeforeLimit()
    }

    @Test
    fun rollbackAndSavepointsKeepDurableHistoryAndHistoricalReads() = runBoundedIntegrationTest {
        cases.rollbackAndSavepointsKeepDurableHistoryAndHistoricalReads()
    }

    @Test
    fun deletedRecordHistoryPreservesLimitsAndCursorCeiling() = runBoundedIntegrationTest {
        cases.deletedRecordHistoryPreservesLimitsAndCursorCeiling()
    }

    @Test
    fun projectedHistoryKeepsDeletionAndRestorationWithinBounds() = runBoundedIntegrationTest {
        cases.projectedHistoryKeepsDeletionAndRestorationWithinBounds()
    }

    @Test
    fun changesAndUpdatesRespectDeletionAndRestorationWindows() = runBoundedIntegrationTest {
        cases.changesAndUpdatesRespectDeletionAndRestorationWindows()
    }

    @Test
    fun filteredCursorKeepsTiesAfterLaterSoftDeletion() = runBoundedIntegrationTest {
        cases.filteredCursorKeepsTiesAfterLaterSoftDeletion()
    }

    @Test
    fun hardDeleteHistorySurvivesKeyReuseWithinItsWindow() = runBoundedIntegrationTest {
        cases.hardDeleteHistorySurvivesKeyReuseWithinItsWindow()
    }

    @Test
    fun transactionHistoryPreservesOrderedDurableRevisions() = runBoundedIntegrationTest {
        cases.transactionHistoryPreservesOrderedDurableRevisions()
    }
}
