package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.deleteFolder
import maryk.rocksdb.util.createTestDBFolder
import kotlin.test.Test

class RocksDBDataStoreTest {
    @Test
    fun testDataStore() = runTest {
        val folder = createTestDBFolder("no-history")

        val dataStore = RocksDBDataStore(
            relativePath = createTestDBFolder("no-history"),
            dataModelsById = dataModelsForTests,
            keepAllVersions = false
        )

        runDataStoreTests(dataStore)

        dataStore.close()

        deleteFolder(folder)
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest {
        val folder = createTestDBFolder("history")

        val dataStore = RocksDBDataStore(
            relativePath = folder,
            keepAllVersions = true,
            dataModelsById = dataModelsForTests
        )

        runDataStoreTests(dataStore)

        dataStore.close()

        deleteFolder(folder)
    }
}
