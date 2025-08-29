package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import kotlin.test.Test

class FoundationDBDataStoreTest {
    @Test
    fun testDataStore() = runTest {
        val dataStore = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest {
        val dataStore = FoundationDBDataStore.open(
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }
}
