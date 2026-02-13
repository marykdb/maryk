@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package maryk.datastore.foundationdb

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.Log
import maryk.test.models.Severity.INFO
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ClusterUpdateLogObservabilityTest {
    @Test
    fun clusterUpdateLogStatsAreNullWhenDisabled() = runBlocking {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "observability", "disabled", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = false,
        )
        try {
            assertNull(store.getClusterUpdateLogStats())
        } finally {
            store.close()
        }
    }

    @Test
    fun clusterUpdateLogStatsTrackTailingAndListeners() = runBlocking {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "observability", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = true,
            clusterUpdateLogConsumerId = "single-${Uuid.random()}",
        )

        try {
            val modelId = 4u // Log model id in dataModelsForTests

            val before = store.getClusterUpdateLogStats()
            assertNotNull(before)
            assertEquals(0, before.activeListenerCountsByModelId[modelId] ?: 0)

            store.execute(
                Log.add(
                    Log(message = "no-listener", severity = INFO, timestamp = LocalDateTime(2026, 1, 1, 10, 0, 0))
                )
            ).statuses.forEach { assertIs<AddSuccess<Log>>(it) }

            delay(300)

            val withoutListeners = store.getClusterUpdateLogStats()
            assertNotNull(withoutListeners)
            assertEquals(0, withoutListeners.activeListenerCountsByModelId[modelId] ?: 0)
            assertTrue(withoutListeners.tailTransactions >= before.tailTransactions)
            assertTrue(withoutListeners.decodedUpdates >= before.decodedUpdates)
            assertTrue(withoutListeners.tailErrors >= 0)
            assertTrue(withoutListeners.gcErrors >= 0)

            val updateFlow = store.executeFlow(
                Log.scanUpdates(fromVersion = 0uL)
            )
            // Keep reference alive for flow lifecycle; listener is registered inside executeFlow.
            assertNotNull(updateFlow)

            waitForStat("listener registration") {
                val stats = store.getClusterUpdateLogStats()
                stats != null && (stats.activeListenerCountsByModelId[modelId] ?: 0) > 0
            }

            store.execute(
                Log.add(
                    Log(message = "with-listener", severity = INFO, timestamp = LocalDateTime(2026, 1, 1, 10, 0, 1))
                )
            ).statuses.forEach { assertIs<AddSuccess<Log>>(it) }
            delay(300)

            val withListeners = store.getClusterUpdateLogStats()
            assertNotNull(withListeners)
            assertTrue(withListeners.tailTransactions >= withoutListeners.tailTransactions)
            assertTrue(withListeners.decodedUpdates >= withoutListeners.decodedUpdates)
            assertTrue(withListeners.currentBackoffMs >= 0)
            assertTrue((withListeners.activeListenerCountsByModelId[modelId] ?: 0) > 0)

            store.closeAllListeners()
            delay(50)

            val afterClose = store.getClusterUpdateLogStats()
            assertNotNull(afterClose)
            assertEquals(0, afterClose.activeListenerCountsByModelId[modelId] ?: 0)
        } finally {
            store.close()
        }
    }

    @Test
    fun clusterUpdateLogStatsTrackMultipleListenersPerModel() = runBlocking {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "observability", "multi-listener", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = true,
            clusterUpdateLogConsumerId = "multi-${Uuid.random()}",
        )
        try {
            val logFlow1 = store.executeFlow(Log.scanUpdates(fromVersion = 0uL))
            val logFlow2 = store.executeFlow(Log.scan())
            val modelFlow = store.executeFlow(TestMarykModel.scan())
            assertNotNull(logFlow1)
            assertNotNull(logFlow2)
            assertNotNull(modelFlow)

            waitForStat("multi listener registration") {
                val stats = store.getClusterUpdateLogStats()
                stats != null &&
                    (stats.activeListenerCountsByModelId[4u] ?: 0) == 2 &&
                    (stats.activeListenerCountsByModelId[1u] ?: 0) == 1
            }

            store.closeAllListeners()
            delay(50)

            val afterClose = store.getClusterUpdateLogStats()
            assertNotNull(afterClose)
            assertEquals(0, afterClose.activeListenerCountsByModelId[4u] ?: 0)
            assertEquals(0, afterClose.activeListenerCountsByModelId[1u] ?: 0)
        } finally {
            store.close()
        }
    }
}

private suspend fun waitForStat(name: String, check: () -> Boolean) {
    repeat(60) {
        if (check()) return
        delay(100)
    }
    throw AssertionError("Timed out while waiting for $name")
}
