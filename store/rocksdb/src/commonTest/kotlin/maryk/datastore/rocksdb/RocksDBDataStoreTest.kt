package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.deleteFolder
import maryk.createTestDBFolder
import kotlin.test.Test

class RocksDBDataStoreTest {
    @Test
    fun testDataStore() = runTest {
        val folder = createTestDBFolder("no-history")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )
        try {
            runDataStoreTests(dataStore)
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest {
        val folder = createTestDBFolder("history")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            keepAllVersions = true,
            dataModelsById = dataModelsForTests,
        )
        try {
            runDataStoreTests(dataStore)
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }
}
