package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import kotlin.random.Random
import kotlin.test.Test

class IndexedDBDataStoreTest {
    @Test
    fun runAllDataStoreTests() = runTest {
        val databaseName = "maryk-indexeddb-test-${Random.nextInt(Int.MAX_VALUE)}"

        val dataStore = IndexedDBDataStore.open(
            databaseName = databaseName,
            dataModelsById = dataModelsForTests,
        )

        try {
            runDataStoreTests(dataStore)
        } finally {
            dataStore.close()
        }
    }
}
