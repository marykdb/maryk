package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.ModelContractRegressionCases
import maryk.datastore.test.modelContractModels
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class ModelContractRegressionTest {
    private val cases = ModelContractRegressionCases { _ ->
        InMemoryDataStore.open(
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
