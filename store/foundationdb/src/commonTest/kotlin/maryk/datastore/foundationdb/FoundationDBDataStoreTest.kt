@file:OptIn(ExperimentalUuidApi::class)

package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
            databaseOptionsSetter = {
                setTransactionRetryLimit(3)
                setTransactionMaxRetryDelay(5000)
            }
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "history", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testDataStoreWithUpdateHistoryIndex() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "update-history", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        runDataStoreTests(dataStore, "executeSimpleScanUpdatesRequestWithUpdateHistoryIndex")
        runDataStoreTests(dataStore, "executeHistoryStyleScanUpdatesRequestFallsBackWithoutUpdateHistoryIndex")
        runDataStoreTests(dataStore, "executeScanUpdateHistoryReturnsVersionOrderedEntries")

        dataStore.close()
    }

    @Test
    fun tableScanContinuesAcrossMultipleBatches() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "paged-table-scan", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )

        try {
            val values = Array(700) { index ->
                Log(
                    message = "paged-$index",
                    timestamp = LocalDateTime(2024, 1, 1, 0, index / 60, index % 60)
                )
            }
            dataStore.execute(Log.add(*values)).statuses.forEach {
                assertIs<AddSuccess<Log>>(it)
            }

            val response = dataStore.execute(Log.scan(limit = 700u))

            assertEquals(700, response.values.size)
        } finally {
            dataStore.close()
        }
    }
}
