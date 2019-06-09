package maryk.datastore.memory

import maryk.datastore.test.runDataStoreTests
import kotlin.test.Test

class InMemoryDataStoreTest {
    @Test
    fun testDataStore() {
        val dataStore = InMemoryDataStore()

        runDataStoreTests(dataStore)
    }

    @Test
    fun testDataStoreWithKeepAllVersions() {
        val dataStore = InMemoryDataStore(keepAllVersions = true)

        runDataStoreTests(dataStore)
    }
}
