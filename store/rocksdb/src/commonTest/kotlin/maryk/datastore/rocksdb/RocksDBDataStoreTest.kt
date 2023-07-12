package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import kotlin.test.Test

class RocksDBDataStoreTest {
    private val basePath = "./build/test-database"

    @Test
    fun testDataStore() = runTest {
        val dataStore = RocksDBDataStore(
            relativePath = "$basePath/no-history",
            dataModelsById = dataModelsForTests,
            keepAllVersions = false
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest {
        val dataStore = RocksDBDataStore(
            relativePath = "$basePath/history",
            keepAllVersions = true,
            dataModelsById = dataModelsForTests
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }
}
