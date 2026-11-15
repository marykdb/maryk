package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.HistoricalOwnershipConformance
import maryk.datastore.test.historicalOwnershipModels
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class HistoricalOwnershipConformanceTest {
    private val cases = HistoricalOwnershipConformance { name ->
        installIndexedDbForTests()
        IndexedDbDataStore.open(
            databaseName = "$name-${Random.nextInt()}",
            keepAllVersions = true,
            dataModelsById = historicalOwnershipModels,
        )
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
