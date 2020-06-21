package maryk.datastore.rocksdb

import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.test.runSuspendingTest
import kotlin.test.Test

class RocksDBDataStoreTest {
    private val basePath = "./build/test-database"

    @Test
    fun testDataStore() = runSuspendingTest {
        val dataStore = RocksDBDataStore(
            relativePath = "$basePath/no-history",
            dataModelsById = dataModelsForTests,
            keepAllVersions = false
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runSuspendingTest {
        val dataStore = RocksDBDataStore(
            relativePath = "$basePath/history",
            keepAllVersions = true,
            dataModelsById = dataModelsForTests
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }
}
