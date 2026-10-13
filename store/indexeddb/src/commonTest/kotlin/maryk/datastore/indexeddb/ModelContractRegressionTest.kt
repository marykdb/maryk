package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.ModelContractRegressionCases
import maryk.datastore.test.modelContractModels
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.random.Random

class ModelContractRegressionTest {
    private val cases = ModelContractRegressionCases { name ->
        installIndexedDbForTests()
        IndexedDbDataStore.open(
            databaseName = "$name-${Random.nextInt()}",
            keepAllVersions = true, keepUpdateHistoryIndex = true, dataModelsById = modelContractModels,
        )
    }

    @Test
    fun collectionChecksUseCompleteValuesAndRejectMissingProperties() = runTest(timeout = 3.minutes) {
        cases.collectionChecksUseCompleteValuesAndRejectMissingProperties()
    }

    @Test
    fun variableLengthBoundariesKeepExactUpperAndStrictLowerSemantics() = runTest(timeout = 3.minutes) {
        cases.variableLengthBoundariesKeepExactUpperAndStrictLowerSemantics()
    }

    @Test
    fun inclusiveCompositeDateBoundaryPagesInBothDirections() = runTest(timeout = 3.minutes) {
        cases.inclusiveCompositeDateBoundaryPagesInBothDirections()
    }

    @Test
    fun collectionReplacementSurvivesTransactionAndAtomicTransport() = runTest(timeout = 3.minutes) {
        cases.collectionReplacementSurvivesTransactionAndAtomicTransport()
    }
}
