package maryk.datastore.foundationdb

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDateTime
import maryk.core.clock.HLC
import maryk.core.properties.types.Bytes
import maryk.core.query.requests.add
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.foundationdb.clusterlog.ClusterLogDeletion
import maryk.datastore.foundationdb.clusterlog.ClusterUpdateLog
import maryk.datastore.test.dataModelsForTests
import maryk.foundationdb.MutationType
import maryk.test.models.Log
import maryk.test.models.Severity.INFO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class ClusterUpdateLogReliabilityTest {
    @Test
    fun clusterLogCursorUsesCommitOrderWhenHlcDecreases() = runBoundedIntegrationTest {
        val root = listOf("maryk", "test", "cluster-log", "commit-order", Uuid.random().toString())
        val writer = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "writer-${Uuid.random()}",
            )
        )
        val reader = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "reader-${Uuid.random()}",
            )
        )

        try {
            val modelId = 4u
            val key = Bytes(ByteArray(16) { 7 })
            val writerLog = writer.clusterUpdateLog ?: error("Cluster log must be enabled")
            writer.runTransaction { tr ->
                for (version in listOf(10uL, 20uL, 30uL)) {
                    val legacyUpdate = ClusterLogDeletion(key, version = version, hardDelete = false)
                    tr.mutate(
                        MutationType.SET_VERSIONSTAMPED_KEY,
                        writerLog.buildLegacyLogKey(modelId, legacyUpdate),
                        writerLog.encodeValue(modelId, legacyUpdate, Log),
                    )
                }
            }
            for (version in listOf(40uL, 5uL, 6uL)) {
                writer.runTransaction { tr ->
                    writerLog.append(tr, modelId, ClusterLogDeletion(key, version = version, hardDelete = false))
                }
            }

            val readerLog = reader.clusterUpdateLog ?: error("Cluster log must be enabled")
            var populatedShard = -1
            var first: ClusterUpdateLog.TailResult? = null
            for (shard in 0 until 64) {
                val tail = reader.runTransaction { tr ->
                    readerLog.tailOnce(tr, shard, modelId, cursorKey = null, limit = 1)
                }
                if (tail.decoded.isNotEmpty()) {
                    populatedShard = shard
                    first = tail
                    break
                }
            }

            val firstTail = assertNotNull(first)
            assertEquals(listOf(10uL), firstTail.decoded.map { it.update.version })
            reader.runTransaction { tr ->
                readerLog.writeCursorKey(tr, populatedShard, modelId, assertNotNull(firstTail.lastKey))
            }
            val resumedMidDrain = reader.runTransaction { tr ->
                readerLog.readCursorKey(tr, populatedShard, modelId)
            }
            val secondTail = reader.runTransaction { tr ->
                readerLog.tailOnce(
                    tr,
                    populatedShard,
                    modelId,
                    cursorKey = assertNotNull(resumedMidDrain),
                    limit = 1,
                )
            }
            assertEquals(listOf(20uL), secondTail.decoded.map { it.update.version })
            val thirdTail = reader.runTransaction { tr ->
                readerLog.tailOnce(
                    tr,
                    populatedShard,
                    modelId,
                    cursorKey = assertNotNull(secondTail.lastKey),
                    limit = 1,
                )
            }
            assertEquals(listOf(30uL), thirdTail.decoded.map { it.update.version })

            val activation = reader.runTransaction { tr ->
                readerLog.tailOnce(
                    tr,
                    populatedShard,
                    modelId,
                    cursorKey = assertNotNull(thirdTail.lastKey),
                    limit = 1,
                )
            }
            assertTrue(activation.decoded.isEmpty())
            reader.runTransaction { tr ->
                readerLog.writeCursorKey(tr, populatedShard, modelId, assertNotNull(activation.lastKey))
            }
            val resumedV2Cursor = reader.runTransaction { tr ->
                readerLog.readCursorKey(tr, populatedShard, modelId)
            }
            val v2Tail = reader.runTransaction { tr ->
                readerLog.tailOnce(
                    tr,
                    populatedShard,
                    modelId,
                    cursorKey = assertNotNull(resumedV2Cursor),
                    limit = 10,
                )
            }
            assertEquals(listOf(40uL, 5uL, 6uL), v2Tail.decoded.map { it.update.version })
            assertTrue(v2Tail.decoded.zipWithNext().all { (firstUpdate, secondUpdate) ->
                assertNotNull(firstUpdate.commitVersion) < assertNotNull(secondUpdate.commitVersion)
            })
            val noDuplicate = reader.runTransaction { tr ->
                readerLog.tailOnce(
                    tr,
                    populatedShard,
                    modelId,
                    cursorKey = assertNotNull(v2Tail.lastKey),
                    limit = 10,
                )
            }
            assertTrue(noDuplicate.decoded.isEmpty())

            var cutoffCursor = readerLog.initialCursorAtOrAfter(populatedShard, modelId, cutoff = 15uL)
            val retainedLegacy = reader.runTransaction { tr ->
                readerLog.tailOnce(
                    tr,
                    populatedShard,
                    modelId,
                    cursorKey = cutoffCursor,
                    limit = 10,
                )
            }
            assertEquals(listOf(20uL, 30uL), retainedLegacy.decoded.map { it.update.version })
            cutoffCursor = assertNotNull(retainedLegacy.lastKey)
            val cutoffActivation = reader.runTransaction { tr ->
                readerLog.tailOnce(tr, populatedShard, modelId, cutoffCursor, limit = 10)
            }
            cutoffCursor = assertNotNull(cutoffActivation.lastKey)
            val retainedV2 = reader.runTransaction { tr ->
                readerLog.tailOnce(tr, populatedShard, modelId, cutoffCursor, limit = 10)
            }
            assertEquals(listOf(40uL), retainedV2.decoded.map { it.update.version })

            var gcTransactions = 0
            var hasMore: Boolean
            do {
                hasMore = reader.runTransaction { tr ->
                    readerLog.clearBefore(
                        tr,
                        populatedShard,
                        modelId,
                        cutoff = 15uL,
                        limit = 10,
                        maxAffectedKeyBytes = 1,
                    )
                }
                gcTransactions++
            } while (hasMore)
            assertEquals(2, gcTransactions)
            val afterGcLegacy = reader.runTransaction { tr ->
                readerLog.tailOnce(tr, populatedShard, modelId, cursorKey = null, limit = 10)
            }
            assertEquals(listOf(20uL, 30uL), afterGcLegacy.decoded.map { it.update.version })
            val afterGcActivation = reader.runTransaction { tr ->
                readerLog.tailOnce(tr, populatedShard, modelId, assertNotNull(afterGcLegacy.lastKey), limit = 10)
            }
            val afterGcV2 = reader.runTransaction { tr ->
                readerLog.tailOnce(tr, populatedShard, modelId, assertNotNull(afterGcActivation.lastKey), limit = 10)
            }
            assertEquals(listOf(40uL), afterGcV2.decoded.map { it.update.version })
        } finally {
            reader.close()
            writer.close()
        }
    }

    @Test
    fun clusterHlcSyncMaintainsCrossNodeMonotonicWrites() = runBoundedIntegrationTest {
        val root = listOf("maryk", "test", "hlc-reliability", "monotonic", Uuid.random().toString())
        val nodeA = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "node-a-${Uuid.random()}",
            )
        )
        val nodeB = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "node-b-${Uuid.random()}",
            )
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
    fun clusterHlcSyncSurvivesRestartWithStableConsumerId() = runBoundedIntegrationTest {
        val root = listOf("maryk", "test", "hlc-reliability", "restart", Uuid.random().toString())
        val stableConsumerId = "restarted-${Uuid.random()}"

        val writer = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "writer-${Uuid.random()}",
            )
        )

        var restarted = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = stableConsumerId,
            )
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
                clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                    enableClusterUpdateLog = true,
                    clusterUpdateLogConsumerId = stableConsumerId,
                )
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
    fun retentionBoundaryCursorResetStillKeepsHlcSafe() = runBoundedIntegrationTest {
        val root = listOf("maryk", "test", "hlc-reliability", "retention-reset", Uuid.random().toString())
        val writer = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "writer-${Uuid.random()}",
            )
        )
        val reader = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "reader-${Uuid.random()}",
            )
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
    fun malformedLogEntryDoesNotStallTailer() = runBoundedIntegrationTest {
        val root = listOf("maryk", "test", "hlc-reliability", "malformed", Uuid.random().toString())
        val writer = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "writer-${Uuid.random()}",
            )
        )
        val reader = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "reader-${Uuid.random()}",
            )
        )

        try {
            // Activate model listener so tail loop reads this model/shards.
            val initialReceived = CompletableDeferred<Unit>()
            val collector = launch {
                reader.executeFlow(Log.scanUpdates(fromVersion = 0uL)).collect {
                    initialReceived.complete(Unit)
                }
            }
            withTimeout(10_000.milliseconds) { initialReceived.await() }

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
            reader.closeAllListeners()
            reader.close()
            writer.close()
        }
    }

    @Test
    fun highContentionParallelWritersStayMonotonicPerNode() = runBoundedIntegrationTest {
        val root = listOf("maryk", "test", "hlc-reliability", "contention", Uuid.random().toString())
        val nodes = List(4) { idx ->
            FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = root,
                dataModelsById = dataModelsForTests,
                keepAllVersions = false,
                clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                    enableClusterUpdateLog = true,
                    clusterUpdateLogConsumerId = "node-$idx-${Uuid.random()}",
                )
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
            }.awaitAll()

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
    fun cursorRewindCanCauseDuplicateDeliveryAtLeastOnce() = runBoundedIntegrationTest {
        val root = listOf("maryk", "test", "hlc-reliability", "duplicates", Uuid.random().toString())
        val readerConsumerId = "reader-${Uuid.random()}"
        val writer = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "writer-${Uuid.random()}",
            )
        )
        var reader = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = readerConsumerId,
            )
        )

        try {
            val firstInitial = CompletableDeferred<Unit>()
            val firstCollector = launch {
                reader.executeFlow(Log.scanUpdates(fromVersion = 0uL)).collect {
                    firstInitial.complete(Unit)
                }
            }
            withTimeout(10_000.milliseconds) { firstInitial.await() }
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
            firstCollector.cancelAndJoin()

            // Simulate restart/rollback of consumer progress to force replay in retention window.
            reader = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = root,
                dataModelsById = dataModelsForTests,
                keepAllVersions = false,
                clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                    enableClusterUpdateLog = true,
                    clusterUpdateLogConsumerId = readerConsumerId,
                )
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
                clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                    enableClusterUpdateLog = true,
                    clusterUpdateLogConsumerId = readerConsumerId,
                )
            )
            val restartedInitial = CompletableDeferred<Unit>()
            val restartedCollector = launch {
                reader.executeFlow(Log.scanUpdates(fromVersion = 0uL)).collect {
                    restartedInitial.complete(Unit)
                }
            }
            withTimeout(10_000.milliseconds) { restartedInitial.await() }

            val second = writer.addLog("dup-second", 2)
            waitForReliabilityStat("decoded updates reflect replay + new update") {
                val stats = reader.getClusterUpdateLogStats()
                stats != null && stats.observedClusterHlc >= second && stats.decodedUpdates >= 2
            }
            restartedCollector.cancelAndJoin()
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
        delay(100.milliseconds)
    }
    throw AssertionError("Timed out while waiting for $name")
}
