package maryk.datastore.memory

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryDataStoreTest {
    @Test
    fun testDataStore() = runTest {
        val dataStore = InMemoryDataStore(dataModelsById = dataModelsForTests)

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest {
        val dataStore = InMemoryDataStore(keepAllVersions = true, dataModelsById = dataModelsForTests)

        runDataStoreTests(dataStore)

        dataStore.close()
    }
}
