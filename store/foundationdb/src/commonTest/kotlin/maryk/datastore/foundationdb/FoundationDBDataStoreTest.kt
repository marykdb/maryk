@file:OptIn(ExperimentalUuidApi::class)

package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class FoundationDBDataStoreTest {
    @Test
    fun testDataStore() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "no-history", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest(timeout = 5.minutes) {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "history", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }
}
