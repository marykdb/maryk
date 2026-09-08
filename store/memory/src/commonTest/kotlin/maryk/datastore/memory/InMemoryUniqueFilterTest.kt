package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.DataStoreScanUniqueTest
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test

class InMemoryUniqueFilterTest {
    @Test
    fun prefixReturnsAllMatches() = runUniqueTest("executeUniquePrefixReturnsAllMatches")

    @Test
    fun valueInReturnsAllMatches() = runUniqueTest("executeUniqueValueInReturnsAllMatches")

    @Test
    fun valueInWithAbsentFirstValue() = runUniqueTest("executeUniqueValueInWithAbsentFirstValue")

    private fun runUniqueTest(name: String) = runTest {
        for (keepAllVersions in listOf(false, true)) {
            val dataStore = InMemoryDataStore.open(
                keepAllVersions = keepAllVersions,
                dataModelsById = mapOf(1u to CompleteMarykModel)
            )
            try {
                val tests = DataStoreScanUniqueTest(dataStore)
                tests.initData()
                tests.allTests.getValue(name)()
            } finally {
                dataStore.close()
            }
        }
    }
}
