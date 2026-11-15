package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.HistoricalOwnershipConformance
import maryk.datastore.test.historicalOwnershipModels
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class HistoricalOwnershipConformanceTest {
    private val cases = HistoricalOwnershipConformance { name ->
        FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", "test", name, Random.nextInt().toString()), keepAllVersions = true, dataModelsById = historicalOwnershipModels)
    }

    @Test
    fun reversedRecordHistoriesPreserveUniqueOwnership() = runTest(timeout = 3.minutes) {
        cases.reversedRecordHistoriesPreserveUniqueOwnership()
    }

    @Test
    fun equalVersionTransfersRetainAppliedOrder() = runTest(timeout = 3.minutes) {
        cases.equalVersionTransfersRetainAppliedOrder()
    }

    @Test
    fun hardDeletedKeyReusePreservesHistoricalIncarnations() = runTest(timeout = 3.minutes) {
        cases.hardDeletedKeyReusePreservesHistoricalIncarnations()
    }
}
