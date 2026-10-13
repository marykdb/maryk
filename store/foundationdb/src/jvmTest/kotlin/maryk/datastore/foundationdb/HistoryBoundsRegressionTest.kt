package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.HistoryBoundsRegressionCases
import maryk.datastore.test.historyBoundsModels
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.random.Random

class HistoryBoundsRegressionTest {
    private val cases = HistoryBoundsRegressionCases { name ->
        FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", name, Random.nextInt().toString()),
            keepAllVersions = true, keepUpdateHistoryIndex = true, dataModelsById = historyBoundsModels,
        )
    }

    @Test
    fun atomicRevisionKeepsAllSelectedChangesAndFiltersBeforeLimit() = runTest(timeout = 3.minutes) {
        cases.atomicRevisionKeepsAllSelectedChangesAndFiltersBeforeLimit()
    }

    @Test
    fun rollbackAndSavepointsKeepDurableHistoryAndHistoricalReads() = runTest(timeout = 3.minutes) {
        cases.rollbackAndSavepointsKeepDurableHistoryAndHistoricalReads()
    }

    @Test
    fun deletedRecordHistoryPreservesLimitsAndCursorCeiling() = runTest(timeout = 3.minutes) {
        cases.deletedRecordHistoryPreservesLimitsAndCursorCeiling()
    }

    @Test
    fun projectedHistoryKeepsDeletionAndRestorationWithinBounds() = runTest(timeout = 3.minutes) {
        cases.projectedHistoryKeepsDeletionAndRestorationWithinBounds()
    }

    @Test
    fun changesAndUpdatesRespectDeletionAndRestorationWindows() = runTest(timeout = 3.minutes) {
        cases.changesAndUpdatesRespectDeletionAndRestorationWindows()
    }

    @Test
    fun filteredCursorKeepsTiesAfterLaterSoftDeletion() = runTest(timeout = 3.minutes) {
        cases.filteredCursorKeepsTiesAfterLaterSoftDeletion()
    }

    @Test
    fun hardDeleteHistorySurvivesKeyReuseWithinItsWindow() = runTest(timeout = 3.minutes) {
        cases.hardDeleteHistorySurvivesKeyReuseWithinItsWindow()
    }

    @Test
    fun transactionHistoryPreservesOrderedDurableRevisions() = runTest(timeout = 3.minutes) {
        cases.transactionHistoryPreservesOrderedDurableRevisions()
    }
}
