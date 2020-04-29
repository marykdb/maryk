package maryk.datastore.memory

import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.test.runSuspendingTest
import kotlin.test.Test

class InMemoryDataStoreTest {
    @Test
    fun testDataStore() = runSuspendingTest {
        val dataStore = InMemoryDataStore(dataModelsById = dataModelsForTests)

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runSuspendingTest {
        val dataStore = InMemoryDataStore(keepAllVersions = true, dataModelsById = dataModelsForTests)

        runDataStoreTests(dataStore)

        dataStore.close()
    }
}
