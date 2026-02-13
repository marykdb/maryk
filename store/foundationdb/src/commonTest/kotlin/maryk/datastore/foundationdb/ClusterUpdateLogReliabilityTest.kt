@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package maryk.datastore.foundationdb

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import maryk.core.clock.HLC
import maryk.core.query.requests.add
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.Log
import maryk.test.models.Severity.INFO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ClusterUpdateLogReliabilityTest {
    @Test
    fun clusterHlcSyncMaintainsCrossNodeMonotonicWrites() = runBlocking {
        val root = listOf("maryk", "test", "hlc-reliability", "monotonic", Uuid.random().toString())
        val nodeA = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = true,
            clusterUpdateLogConsumerId = "node-a-${Uuid.random()}",
        )
        val nodeB = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = true,
            clusterUpdateLogConsumerId = "node-b-${Uuid.random()}",
        )

        try {
            var lastA = 0uL
            var lastB = 0uL
            var sequence = 0
            repeat(10) { index ->
                val versionA = nodeA.addLog("a-$index", sequence++)
                assertTrue(versionA > lastA, "Node A must be monotonic")
                lastA = versionA

                waitForReliabilityStat("nodeB observes nodeA version $versionA") {
                    val stats = nodeB.getClusterUpdateLogStats()
                    stats != null && stats.observedClusterHlc >= versionA
                }

                val versionB = nodeB.addLog("b-$index", sequence++)
                assertTrue(versionB > lastB, "Node B must be monotonic")
                assertTrue(versionB >= versionA, "Node B write must not regress below observed cluster floor")
                lastB = versionB

                waitForReliabilityStat("nodeA observes nodeB version $versionB") {
                    val stats = nodeA.getClusterUpdateLogStats()
                    stats != null && stats.observedClusterHlc >= versionB
                }
            }

            val finalA = nodeA.getClusterUpdateLogStats()
            val finalB = nodeB.getClusterUpdateLogStats()
            assertNotNull(finalA)
            assertNotNull(finalB)
            assertTrue(finalA.hlcSyncTransactions > 0)
            assertTrue(finalB.hlcSyncTransactions > 0)
        } finally {
            nodeB.close()
            nodeA.close()
        }
    }

    @Test
    fun clusterHlcSyncSurvivesRestartWithStableConsumerId() = runBlocking {
        val root = listOf("maryk", "test", "hlc-reliability", "restart", Uuid.random().toString())
        val stableConsumerId = "restarted-${Uuid.random()}"

        val writer = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = true,
            clusterUpdateLogConsumerId = "writer-${Uuid.random()}",
        )

        var restarted = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = true,
            clusterUpdateLogConsumerId = stableConsumerId,
        )

        try {
            var sequence = 0
            val first = writer.addLog("before-restart", sequence++)
            waitForReliabilityStat("restarted node observes first version") {
                val stats = restarted.getClusterUpdateLogStats()
                stats != null && stats.observedClusterHlc >= first
            }

            restarted.close()

            var maxWritten = first
            repeat(5) { i ->
                val v = writer.addLog("offline-$i", sequence++)
                if (v > maxWritten) maxWritten = v
            }

            restarted = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = root,
                dataModelsById = dataModelsForTests,
                keepAllVersions = false,
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = stableConsumerId,
            )

            waitForReliabilityStat("restarted node catches up to max written $maxWritten") {
                val stats = restarted.getClusterUpdateLogStats()
                stats != null && stats.observedClusterHlc >= maxWritten
            }

            val writeAfterRestart = restarted.addLog("after-restart", sequence++)
            assertTrue(writeAfterRestart >= maxWritten, "Restarted node write must not regress below cluster floor")
        } finally {
            restarted.close()
            writer.close()
        }
    }

    @Test
    fun retentionBoundaryCursorResetStillKeepsHlcSafe() = runBlocking {
        val root = listOf("maryk", "test", "hlc-reliability", "retention-reset", Uuid.random().toString())
        val writer = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = true,
            clusterUpdateLogConsumerId = "writer-${Uuid.random()}",
        )
        val reader = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = true,
            clusterUpdateLogConsumerId = "reader-${Uuid.random()}",
        )

        try {
            var sequence = 0
            var lastWritten = 0uL
            repeat(4) { i ->
                val version = writer.addLog("retention-$i", sequence++)
                if (version > lastWritten) lastWritten = version
            }

            waitForReliabilityStat("reader catches initial writes") {
                val stats = reader.getClusterUpdateLogStats()
                stats != null && stats.observedClusterHlc >= lastWritten
            }

            val log = reader.clusterUpdateLog ?: error("Cluster log must be enabled")
            val modelId = 4u
            val shardCount = 64 // default used by tests/open
            val syntheticCutoff = HLC().timestamp + 1_000_000uL
            reader.runTransaction { tr ->
                for (shard in 0 until shardCount) {
                    val cutoffCursor = log.minimalKeyAtOrAfter(shard, modelId, syntheticCutoff)
                    log.writeCursorKey(tr, shard, modelId, cutoffCursor)
                }
            }

            val afterCutoffWrite = writer.addLog("after-cutoff", sequence++)
            waitForReliabilityStat("reader catches post-cutoff write") {
                val stats = reader.getClusterUpdateLogStats()
                stats != null && stats.observedClusterHlc >= afterCutoffWrite
            }

            val readerWrite = reader.addLog("reader-write-after-cutoff", sequence++)
            assertTrue(readerWrite >= afterCutoffWrite, "Reader write must stay at/above observed cluster floor")
        } finally {
            reader.close()
            writer.close()
        }
    }

    @Test
    fun malformedLogEntryDoesNotStallTailer() = runBlocking {
        val root = listOf("maryk", "test", "hlc-reliability", "malformed", Uuid.random().toString())
        val writer = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = true,
            clusterUpdateLogConsumerId = "writer-${Uuid.random()}",
        )
        val reader = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = true,
            clusterUpdateLogConsumerId = "reader-${Uuid.random()}",
        )

        try {
            // Activate model listener so tail loop reads this model/shards.
            reader.executeFlow(Log.scanUpdates(fromVersion = 0uL))

            waitForReliabilityStat("reader listener registered") {
                val stats = reader.getClusterUpdateLogStats()
                stats != null && (stats.activeListenerCountsByModelId[4u] ?: 0) > 0
            }

            val log = reader.clusterUpdateLog ?: error("Cluster log must be enabled")
            val malformedKey = log.minimalKeyAtOrAfter(0, 4u, 0uL) + byteArrayOf(1)
            reader.runTransaction { tr ->
                tr.set(malformedKey, byteArrayOf(0x01, 0x02)) // intentionally invalid encoded payload
            }

            // Produce real update to ensure tail keeps progressing after decode fault.
            val valid = writer.addLog("valid-after-malformed", 1)
            waitForReliabilityStat("reader observes valid write after malformed entry") {
                val stats = reader.getClusterUpdateLogStats()
                stats != null && stats.observedClusterHlc >= valid && stats.decodedUpdates > 0
            }
        } finally {
            reader.close()
            writer.close()
        }
    }

    @Test
    fun highContentionParallelWritersStayMonotonicPerNode() = runBlocking {
        val root = listOf("maryk", "test", "hlc-reliability", "contention", Uuid.random().toString())
        val nodes = List(4) { idx ->
            FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = root,
                dataModelsById = dataModelsForTests,
                keepAllVersions = false,
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "node-$idx-${Uuid.random()}",
            )
        }

        try {
            val results = nodes.mapIndexed { nodeIndex, node ->
                async {
                    val versions = mutableListOf<ULong>()
                    repeat(25) { i ->
                        versions += node.addLog("node-$nodeIndex-$i", (nodeIndex * 1000) + i)
                    }
                    versions
                }
            }.map { it.await() }

            for (versions in results) {
                assertEquals(25, versions.size)
                for (i in 1 until versions.size) {
                    assertTrue(versions[i] > versions[i - 1], "Per-node writes must be strictly monotonic")
                }
            }
        } finally {
            nodes.forEach { it.close() }
        }
    }

    @Test
    fun cursorRewindCanCauseDuplicateDeliveryAtLeastOnce() = runBlocking {
        val root = listOf("maryk", "test", "hlc-reliability", "duplicates", Uuid.random().toString())
        val readerConsumerId = "reader-${Uuid.random()}"
        val writer = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = true,
            clusterUpdateLogConsumerId = "writer-${Uuid.random()}",
        )
        var reader = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            enableClusterUpdateLog = true,
            clusterUpdateLogConsumerId = readerConsumerId,
        )

        try {
            reader.executeFlow(Log.scanUpdates(fromVersion = 0uL))
            waitForReliabilityStat("reader listener active") {
                val stats = reader.getClusterUpdateLogStats()
                stats != null && (stats.activeListenerCountsByModelId[4u] ?: 0) > 0
            }

            val first = writer.addLog("dup-first", 1)
            waitForReliabilityStat("reader processes first update") {
                val stats = reader.getClusterUpdateLogStats()
                stats != null && stats.observedClusterHlc >= first && stats.decodedUpdates > 0
            }
            reader.close()

            // Simulate restart/rollback of consumer progress to force replay in retention window.
            reader = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = root,
                dataModelsById = dataModelsForTests,
                keepAllVersions = false,
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = readerConsumerId,
            )
            val log = reader.clusterUpdateLog ?: error("Cluster log must be enabled")
            val modelId = 4u
            val shardCount = 64 // default
            reader.runTransaction { tr ->
                for (shard in 0 until shardCount) {
                    log.writeCursorKey(tr, shard, modelId, log.minimalKeyAtOrAfter(shard, modelId, 0uL))
                }
            }
            reader.close()

            reader = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = root,
                dataModelsById = dataModelsForTests,
                keepAllVersions = false,
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = readerConsumerId,
            )
            reader.executeFlow(Log.scanUpdates(fromVersion = 0uL))
            val beforeReplay = reader.getClusterUpdateLogStats() ?: error("stats missing")

            writer.addLog("dup-second", 2)
            waitForReliabilityStat("decoded updates reflect replay + new update") {
                val stats = reader.getClusterUpdateLogStats()
                stats != null && stats.decodedUpdates >= beforeReplay.decodedUpdates + 2
            }
        } finally {
            reader.close()
            writer.close()
        }
    }
}

private suspend fun FoundationDBDataStore.addLog(message: String, sequence: Int): ULong {
    val second = sequence % 60
    val nanos = (sequence % 1_000_000) * 1_000
    val status = execute(
        Log.add(
            Log(message = message, severity = INFO, timestamp = LocalDateTime(2026, 1, 1, 12, 0, second, nanos))
        )
    ).statuses.single()
    return assertIs<AddSuccess<Log>>(status).version
}

private suspend fun waitForReliabilityStat(name: String, check: () -> Boolean) {
    repeat(80) {
        if (check()) return
        delay(100)
    }
    throw AssertionError("Timed out while waiting for $name")
}
