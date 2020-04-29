package maryk.datastore.rocksdb

import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.rocksdb.DBOptions
import maryk.test.runSuspendingTest
import kotlin.test.Test

class RocksDBDataStoreTest {
    private val rocksDBOptions = DBOptions().apply {
        setCreateIfMissing(true)
        setCreateMissingColumnFamilies(true)
    }

    private val basePath = "./build/test-database"

    @Test
    fun testDataStore() = runSuspendingTest {
        val dataStore = RocksDBDataStore(
            relativePath = "$basePath/no-history",
            rocksDBOptions = rocksDBOptions,
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
            rocksDBOptions = rocksDBOptions,
            keepAllVersions = true,
            dataModelsById = dataModelsForTests
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }
}
