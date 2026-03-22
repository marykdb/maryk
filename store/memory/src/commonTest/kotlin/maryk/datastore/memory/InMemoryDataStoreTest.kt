package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import kotlin.test.Test

class InMemoryDataStoreTest {
    @Test
    fun testDataStore() = runTest {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        try {
            runDataStoreTests(dataStore)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest {
        val dataStore = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = dataModelsForTests)
        try {
            runDataStoreTests(dataStore)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun testDataStoreWithUpdateHistoryIndex() = runTest {
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = dataModelsForTests
        )
        try {
            runDataStoreTests(dataStore, "executeSimpleScanUpdatesRequestWithUpdateHistoryIndex")
            runDataStoreTests(dataStore, "executeScanUpdatesRequestWithUpdateHistoryIndexReturnsChangeUpdates")
            runDataStoreTests(dataStore, "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndex")
            runDataStoreTests(dataStore, "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexTracksNewTopKey")
            runDataStoreTests(dataStore, "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexRefillsAfterDeletion")
        } finally {
            dataStore.close()
        }
    }
}
