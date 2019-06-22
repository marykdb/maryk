package maryk.datastore.rocksdb

import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.rocksdb.Options
import kotlin.test.Test

class RocksDBDataStoreTest {
    private val rocksDBOptions = Options().apply {
        setCreateIfMissing(true)
        setCreateMissingColumnFamilies(true)
    }

    private val basePath = "./build/test-database"

    @Test
    fun testDataStore() {
        val dataStore = RocksDBDataStore(
            relativePath = "$basePath/no-history",
            rocksDBOptions = rocksDBOptions,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false
        )

        runDataStoreTests(dataStore)
    }

    @Test
    fun testDataStoreWithKeepAllVersions() {
        val dataStore = RocksDBDataStore(
            relativePath = "$basePath/history",
            rocksDBOptions = rocksDBOptions,
            keepAllVersions = true,
            dataModelsById = dataModelsForTests
        )

        runDataStoreTests(dataStore)
    }
}
