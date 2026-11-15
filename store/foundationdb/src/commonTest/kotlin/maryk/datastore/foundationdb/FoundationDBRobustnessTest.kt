package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.models.key
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Version
import maryk.core.query.orders.ascending
import maryk.core.query.requests.scan
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.requests.get
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.unwrapFdb
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.shared.beginTransaction
import maryk.foundationdb.FDBException
import maryk.foundationdb.Range
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private object RobustRecord : RootDataModel<RobustRecord>(minimumKeyScanByteRange = 0u) {
    val label by string(1u, unique = true)
    val payload by map(2u, required = false, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
    val allocated by incrementingMap(3u, required = false, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
}

private object MaintenanceBefore : RootDataModel<MaintenanceBefore>(name = "MaintenanceRecord", version = Version(1)) {
    val label by string(1u)
    val payload by string(2u)
}

private object MaintenanceAfter : RootDataModel<MaintenanceAfter>(name = "MaintenanceRecord", version = Version(1, 1),
    indexes = { listOf(MaintenanceAfter.label.ref()) }) {
    val label by string(1u)
    val payload by string(2u)
}

class FoundationDBRobustnessTest {
    @Test
    fun ambiguousCommitReplaysAnAllocationInsteadOfApplyingItTwice() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val added = assertIs<AddSuccess<RobustRecord>>(store.execute(RobustRecord.add(
                RobustRecord.create { label with "original"; allocated with emptyMap() },
            )).statuses.single())
            val once = atomic(true)
            store.afterOrdinaryMutationPrepared.value = { transaction ->
                if (once.getAndSet(false)) {
                    transaction.commit().awaitResult()
                    throw injectedUnknownCommit()
                }
            }
            val changed = store.execute(RobustRecord.change(added.key.change(
                IncMapChange(RobustRecord.ref { allocated }.change(addValues = listOf("exactly once"))),
            )))
            assertIs<ChangeSuccess<RobustRecord>>(changed.statuses.single())
            assertEquals(1, store.ordinaryReceiptReplays.value)
            assertEquals(listOf("exactly once"), store.execute(RobustRecord.get(added.key)).values.single().values { allocated }!!.values.toList())
            assertTrue(store.largestOrdinaryTransactionBytes.value > 0)
        } finally { store.close() }
    }

    @Test
    fun ambiguousAddAndDeleteReturnTheirOriginalSuccessAndEmitOnce() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val failNext = atomic(true)
            val notifications = atomic(0)
            store.beforeUpdateEmission.value = { notifications.incrementAndGet() }
            store.afterOrdinaryMutationPrepared.value = { transaction ->
                if (failNext.getAndSet(false)) {
                    transaction.commit().awaitResult()
                    throw injectedUnknownCommit()
                }
            }
            val added = assertIs<AddSuccess<RobustRecord>>(store.execute(RobustRecord.add(
                RobustRecord.create { label with "ambiguous add" },
            )).statuses.single())
            failNext.value = true
            assertIs<DeleteSuccess<RobustRecord>>(store.execute(RobustRecord.delete(added.key)).statuses.single())
            assertEquals(2, store.ordinaryReceiptReplays.value)
            assertEquals(2, notifications.value)
            assertTrue(store.execute(RobustRecord.get(added.key)).values.isEmpty())
        } finally { store.close() }
    }

    @Test
    fun configuredWorkBudgetRejectsAnOversizedMutationWithoutCommitting() = runBoundedIntegrationTest {
        val store = openStore(execution = FoundationDBExecutionConfiguration(maxTransactionWorkBytes = 10_000_000))
        try {
            val values = RobustRecord.create {
                label with "oversized"; payload with (0 until 1_000).associate { "$it" to "p".repeat(12_000) }
            }
            val key = RobustRecord.key(values)
            val response = store.execute(RobustRecord.add(key to values))
            val failed = assertIs<ServerFail<RobustRecord>>(response.statuses.single())
            assertIs<FoundationDBTransactionWorkLimitExceededException>(failed.cause)
            assertTrue(store.execute(RobustRecord.get(key)).values.isEmpty())
        } finally { store.close() }
    }

    @Test
    fun nativeAdmissionRejectsOversizedWritesWithoutWorkSizeInstrumentation() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val values = RobustRecord.create {
                label with "native oversized"; payload with (0 until 1_000).associate { "$it" to "p".repeat(12_000) }
            }
            val key = RobustRecord.key(values)
            val response = store.execute(RobustRecord.add(key to values))
            val failed = assertIs<ServerFail<RobustRecord>>(response.statuses.single())
            assertEquals(2101, assertIs<FDBException>(failed.cause).getCode())
            assertEquals(0L, store.largestOrdinaryTransactionBytes.value)
            assertTrue(store.execute(RobustRecord.get(key)).values.isEmpty())
            assertIs<AddSuccess<RobustRecord>>(store.execute(RobustRecord.add(
                RobustRecord.create { label with "usable after native rejection" },
            )).statuses.single())
            Unit
        } finally { store.close() }
    }

    @Test
    fun closeReclaimsOnlyItsOwnPrivateReceiptNamespace() = runBoundedIntegrationTest {
        val root = listOf("maryk", "receipt-close", Uuid.random().toString())
        val first = openStore(root)
        val second = openStore(root)
        val firstKey = first.ordinaryReceiptKey()
        val secondKey = second.ordinaryReceiptKey()
        try {
            first.tc.run { it.set(firstKey, byteArrayOf(1)) }
            second.tc.run { it.set(secondKey, byteArrayOf(1)) }
            first.close()
            assertEquals(null, second.tc.run { it.get(firstKey).awaitResult() })
            assertContentEquals(byteArrayOf(1), second.tc.run { it.get(secondKey).awaitResult() })
        } finally { first.close(); second.close() }
    }

    @Test
    fun concurrentReceiptOwnersKeepTheirWriteConflictWhenHooksSuppressAutomaticRanges() = runBoundedIntegrationTest {
        val count = 16
        val root = listOf("maryk", "receipt-owner-conflicts", Uuid.random().toString())
        val enabled = atomic(false)
        val arrivals = atomic(0)
        val ready = CompletableDeferred<Unit>()
        val configuration = FoundationDBTransactionConfiguration(optionsSetter = { context ->
            if (enabled.value && context.kind == FoundationDBTransactionKind.Maintenance && context.dataModelId == null) {
                setNextWriteNoWriteConflictRange()
                if (arrivals.incrementAndGet() == count) ready.complete(Unit)
                runBlocking { withTimeout(5.seconds) { ready.await() } }
            }
        })
        val stores = List(count) {
            FoundationDBDataStore.open(directoryPath = root, dataModelsById = mapOf(1u to RobustRecord),
                transactionConfiguration = configuration)
        }
        try {
            enabled.value = true
            val keys = stores.map { store -> async(Dispatchers.IO) { store.ordinaryReceiptKey() } }.awaitAll()
            assertEquals(count, keys.map { it.toHexString() }.toSet().size)
        } finally {
            enabled.value = false
            ready.complete(Unit)
            stores.forEach { it.close() }
        }
    }

    @Test
    fun readsAndNoOpMutationsDoNotAllocateAPrivateReceiptOwner() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val missing = RobustRecord.key(RobustRecord.create { label with "missing" })
            assertTrue(store.execute(RobustRecord.get(missing)).values.isEmpty())
            store.execute(RobustRecord.delete(missing))
            assertFailsWith<IllegalStateException> { store.ordinaryReceiptRange() }
            assertIs<AddSuccess<RobustRecord>>(store.execute(RobustRecord.add(
                RobustRecord.create { label with "actual mutation" },
            )).statuses.single())
            val first = store.ordinaryReceiptKey()
            val second = store.ordinaryReceiptKey()
            assertTrue(!first.contentEquals(second))
        } finally { store.close() }
    }

    @Test
    fun defaultAdmissionAllowsLargeStagedWorkWithASmallFinalCommit() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val added = assertIs<AddSuccess<RobustRecord>>(store.execute(RobustRecord.add(RobustRecord.create {
                label with "overwritten"; payload with mapOf("a" to "initial")
            })).statuses.single())
            assertEquals(0L, store.largestOrdinaryTransactionBytes.value)
            // Request internal instrumentation without configuring a conservative work limit.
            store.afterOrdinaryMutationPrepared.value = {}
            val changes = (0 until 300).map { index ->
                Change(RobustRecord.ref { payload } with mapOf("a" to "$index-${"p".repeat(20_000)}"))
            }
            assertIs<ChangeSuccess<RobustRecord>>(store.execute(RobustRecord.change(
                added.key.change(*changes.toTypedArray()),
            )).statuses.single())
            assertTrue(store.largestOrdinaryTransactionBytes.value > 10_000_000,
                "The native work estimate should count the overwritten values")
            assertTrue(store.execute(RobustRecord.get(added.key)).values.single().values { payload }!!.getValue("a").startsWith("299-"))
        } finally { store.close() }
    }

    @Test
    fun readOnlyMaintenanceAndUnchangedWritesDoNotInvalidateSuspendedScopes() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val values = RobustRecord.create { label with "unchanged" }
            val added = assertIs<AddSuccess<RobustRecord>>(store.execute(RobustRecord.add(values)).statuses.single())
            val transaction = store.beginTransaction()
            transaction.execute(RobustRecord.get(added.key))
            val revision = store.tc.run { it.get(store.transactionRevisionKey).awaitResult() }
            store.runTransaction(1u) { it.get(store.transactionRevisionKey).awaitResult() }
            assertIs<AlreadyExists<RobustRecord>>(store.execute(RobustRecord.add(added.key to values)).statuses.single())
            assertIs<ChangeSuccess<RobustRecord>>(store.execute(RobustRecord.change(added.key.change(
                Change(RobustRecord.ref { label } with "unchanged"),
            ))).statuses.single())
            assertContentEquals(revision, store.tc.run { it.get(store.transactionRevisionKey).awaitResult() })
            assertEquals(TransactionStatus.Committed, transaction.commit().status)
            val conflicted = store.beginTransaction()
            conflicted.execute(RobustRecord.get(added.key))
            store.execute(RobustRecord.change(added.key.change(Change(RobustRecord.ref { label } with "changed"))))
            assertEquals(TransactionStatus.Aborted, conflicted.commit().status)
        } finally { store.close() }
    }

    @Test
    fun materializationBudgetsRejectWideReadsButPermitProjectionAndWrites() = runBoundedIntegrationTest {
        val store = openStore(execution = FoundationDBExecutionConfiguration(maxRecordReadBytes = 2_048, maxRequestReadBytes = 4_096))
        try {
            val added = assertIs<AddSuccess<RobustRecord>>(store.execute(RobustRecord.add(
                RobustRecord.create { label with "wide"; payload with (0..15).associate { "$it" to "x".repeat(500) } },
            )).statuses.single())
            assertFailsWith<FoundationDBReadLimitExceededException> { store.execute(RobustRecord.get(added.key)) }
            assertEquals("wide", store.execute(RobustRecord.get(added.key, select = RobustRecord.graph { listOf(label) })).values.single().values { label })
        } finally { store.close() }
    }

    @Test
    fun bulkImportPreservesStatusOrderAndUniqueConstraints() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val objects = (0 until 40).map { RobustRecord.create { label with "bulk-$it" } }
            val keys = objects.map { RobustRecord.key(it) }
            val response = store.bulkAdd(RobustRecord.add(*keys.zip(objects).toTypedArray()), concurrency = 8)
            assertEquals(keys, response.statuses.map { assertIs<AddSuccess<RobustRecord>>(it).key })
            assertTrue(store.bulkAdd(RobustRecord.add(*keys.zip(objects).toTypedArray()), concurrency = 8)
                .statuses.all { it is AlreadyExists<*> })
            assertEquals(keys.toSet(), store.execute(RobustRecord.get(*keys.toTypedArray())).values.map { it.key }.toSet())
        } finally { store.close() }
    }

    @Test
    fun committedWatchesWakeBeforeThePollingFallback() = runBoundedIntegrationTest {
        val root = listOf("maryk", "watch-wakeup", Uuid.random().toString())
        val reader = openStore(root, "reader")
        val writer = openStore(root, "writer")
        try {
            val received = CompletableDeferred<Unit>()
            val initial = CompletableDeferred<Unit>()
            val collector = launch {
                reader.executeFlow(RobustRecord.scanUpdates(fromVersion = 0uL)).collect {
                    initial.complete(Unit)
                    if (it is AdditionUpdate<*>) received.complete(Unit)
                }
            }
            try {
                withTimeout(10.seconds) {
                    initial.await()
                    while (reader.clusterLogWatchRegistrations.value < 16) delay(10)
                }
                assertIs<AddSuccess<RobustRecord>>(writer.execute(RobustRecord.add(
                    RobustRecord.create { label with "wake immediately" },
                )).statuses.single())
                withTimeout(2.seconds) { received.await() }
            } finally { collector.cancelAndJoin() }
        } finally { reader.close(); writer.close() }
    }

    @Test
    fun aBusyShardCannotStarveAnotherShard() = runBoundedIntegrationTest {
        val root = listOf("maryk", "fair-tailing", Uuid.random().toString())
        val writer = openStore(root, "writer")
        val reader = openStore(root, "reader", batchSize = 1)
        try {
            // FNV's parity for model 1 and zero-filled UUID keys places these in shards 0 and 1.
            val hot = RobustRecord.key(ByteArray(RobustRecord.Meta.keyByteSize))
            val cold = RobustRecord.key(ByteArray(RobustRecord.Meta.keyByteSize).also { it[it.lastIndex] = 1 })
            writer.execute(RobustRecord.add(hot to RobustRecord.create { label with "hot" },
                cold to RobustRecord.create { label with "cold" }))
            val refills = atomic(0)
            val coldVisited = CompletableDeferred<Unit>()
            reader.afterClusterTailBatch.value = { shard, decoded ->
                if (decoded && shard == 0 && refills.value < 20) {
                    val next = refills.incrementAndGet()
                    runBlocking { writer.execute(RobustRecord.change(hot.change(Change(RobustRecord.ref { label } with "hot-$next")))) }
                }
                if (decoded && shard == 1) coldVisited.complete(Unit)
            }
            val collector = launch { reader.executeFlow(RobustRecord.scanUpdates(fromVersion = 0uL)).collect {} }
            try {
                withTimeout(10.seconds) { coldVisited.await() }
                assertTrue(refills.value <= 2, "Cold shard must be visited while the hot shard still has backlog; refills=${refills.value}")
            } finally { reader.afterClusterTailBatch.value = null; collector.cancelAndJoin() }
        } finally { reader.close(); writer.close() }
    }

    @Test
    fun requestBudgetBoundsSeveralIndividuallySmallRecords() = runBoundedIntegrationTest {
        val store = openStore(execution = FoundationDBExecutionConfiguration(maxRecordReadBytes = 2_048, maxRequestReadBytes = 4_096))
        try {
            val keys = store.execute(RobustRecord.add(*Array(5) { index -> RobustRecord.create {
                label with "small-$index"; payload with mapOf("a" to "p".repeat(650), "b" to "q".repeat(650))
            } })).statuses.map { assertIs<AddSuccess<RobustRecord>>(it).key }
            assertFailsWith<FoundationDBReadLimitExceededException> { store.execute(RobustRecord.get(*keys.toTypedArray())) }
            assertEquals(5, store.execute(RobustRecord.get(*keys.toTypedArray(), select = RobustRecord.graph { listOf(label) })).values.size)
        } finally { store.close() }
    }

    @Test
    fun partitionedIndexRebuildKeepsEveryRecordAndHistoricIndex() = runBoundedIntegrationTest {
        for (history in listOf(false, true)) {
            val root = listOf("maryk", "partitioned-rebuild", Uuid.random().toString())
            val before = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = root,
                dataModelsById = mapOf(1u to MaintenanceBefore), keepAllVersions = history)
            try {
                val response = before.bulkAdd(MaintenanceBefore.add(*Array(256) { index -> MaintenanceBefore.create {
                    label with "$index".padStart(5, '0'); payload with "p".repeat(16_000)
                } }))
                assertTrue(response.statuses.all { it is AddSuccess<*> })
            } finally { before.close() }
            val after = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = root,
                dataModelsById = mapOf(1u to MaintenanceAfter), keepAllVersions = history,
                transactionConfiguration = FoundationDBTransactionConfiguration(),
                executionConfiguration = FoundationDBExecutionConfiguration(maintenancePartitionBytes = 64 * 1024,
                    maintenanceConcurrency = 4))
            try {
                val progress = after.maintenanceProgress(1u)!!
                assertTrue(progress.partitions > 1, "Wide source rows should produce several whole-record partitions: $progress")
                assertEquals(progress.partitions, progress.completedPartitions)
                val scan = after.execute(MaintenanceAfter.scan(order = MaintenanceAfter.label.ref().ascending(), limit = 256u,
                    select = MaintenanceAfter.graph { listOf(label) }))
                assertEquals((0 until 256).map { "$it".padStart(5, '0') }, scan.values.map { it.values { label } })
                if (history) {
                    val historical = after.execute(MaintenanceAfter.scan(order = MaintenanceAfter.label.ref().ascending(), limit = 256u,
                        select = MaintenanceAfter.graph { listOf(label) }, toVersion = scan.values.maxOf { it.lastVersion }))
                    assertEquals(scan.values.map { it.key }, historical.values.map { it.key })
                }
            } finally { after.close() }
        }
    }

    @Test
    fun cancellingASuspendingAttemptReleasesTheTransactionWithoutCommitting() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val key = packKey(store.getTableDirs(1u).modelPrefix, "cancel-attempt".encodeToByteArray())
            val prepared = CompletableDeferred<Unit>()
            val operation = launch {
                store.runRequestTransaction(1u) { transaction ->
                    transaction.set(key, byteArrayOf(1))
                    store.tc.markMutation(transaction)
                    prepared.complete(Unit)
                    awaitCancellation()
                }
            }
            withTimeout(2.seconds) { prepared.await() }
            withTimeout(2.seconds) { operation.cancelAndJoin() }
            assertEquals(null, store.tc.run { it.get(key).awaitResult() })
        } finally { store.close() }
    }

    @Test
    fun completingOneSuspendingRequestKeepsAnotherOwnedTransactionCancellable() = runBoundedIntegrationTest {
        val root = listOf("maryk", "suspending-ownership", Uuid.random().toString())
        val writer = openStore(root)
        val observer = openStore(root)
        val release = CompletableDeferred<Unit>()
        try {
            val prefix = writer.getTableDirs(1u).modelPrefix
            val firstKey = packKey(prefix, "committed-owner".encodeToByteArray())
            val secondKey = packKey(prefix, "cancelled-owner".encodeToByteArray())
            val prepared = CompletableDeferred<Unit>()
            val second = async {
                runCatching {
                    writer.runRequestTransaction(1u) { transaction ->
                        transaction.set(secondKey, byteArrayOf(2))
                        writer.tc.markMutation(transaction)
                        prepared.complete(Unit)
                        release.await()
                    }
                }
            }
            withTimeout(2.seconds) { prepared.await() }
            writer.runRequestTransaction(1u) { transaction ->
                transaction.set(firstKey, byteArrayOf(1))
                writer.tc.markMutation(transaction)
            }
            writer.tc.cancelActive()
            release.complete(Unit)
            assertTrue(withTimeout(2.seconds) { second.await() }.isFailure)
            assertContentEquals(byteArrayOf(1), observer.tc.run { it.get(firstKey).awaitResult() })
            assertEquals(null, observer.tc.run { it.get(secondKey).awaitResult() })
        } finally {
            release.complete(Unit)
            writer.close()
            observer.close()
        }
    }

    @Test
    fun aSuspendingBodyCannotCommitAfterItCatchesCancellation() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val key = packKey(store.getTableDirs(1u).modelPrefix, "cancelled-body".encodeToByteArray())
            val prepared = CompletableDeferred<Unit>()
            val operation = launch {
                store.tc.runSuspending(FoundationDBTransactionContext(FoundationDBTransactionKind.Request)) { transaction ->
                    transaction.set(key, byteArrayOf(1))
                    prepared.complete(Unit)
                    try { awaitCancellation() } catch (_: CancellationException) {}
                }
            }
            withTimeout(2.seconds) { prepared.await() }
            withTimeout(2.seconds) { operation.cancelAndJoin() }
            assertEquals(null, store.tc.run { it.get(key).awaitResult() })
        } finally { store.close() }
    }

    @Test
    fun suspendingAttemptsWaitForTheirChildrenBeforeCommitting() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val key = packKey(store.getTableDirs(1u).modelPrefix, "structured-child".encodeToByteArray())
            supervisorScope {
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val returned = CompletableDeferred<Unit>()
                val operation = async {
                    store.tc.runSuspending(FoundationDBTransactionContext(FoundationDBTransactionKind.Request)) { transaction ->
                        CoroutineScope(currentCoroutineContext()).launch(start = CoroutineStart.UNDISPATCHED) {
                            started.complete(Unit)
                            release.await()
                            transaction.set(key, byteArrayOf(1))
                        }
                    }
                    returned.complete(Unit)
                }
                try {
                    withTimeout(2.seconds) { started.await() }
                    assertNull(withTimeoutOrNull(100) { returned.await() })
                    release.complete(Unit)
                    withTimeout(2.seconds) { operation.await() }
                    assertContentEquals(byteArrayOf(1), store.tc.run { it.get(key).awaitResult() })
                } finally {
                    release.complete(Unit)
                    operation.cancelAndJoin()
                }
            }
        } finally { store.close() }
    }

    @Test
    fun nativeRetryLimitIsRetainedByASuspendingTransactionOwner() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            var attempts = 0
            assertFailsWith<FDBException> {
                store.tc.runSuspending(FoundationDBTransactionContext(FoundationDBTransactionKind.Request)) { transaction ->
                    attempts++
                    transaction.options().setRetryLimit(0)
                    throw FDBException("injected conflict", 1020)
                }
            }
            assertEquals(1, attempts)
        } finally { store.close() }
    }

    @Test
    fun nativeApiEstimatesSplitsAndCancellationHaveRealEffects() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val prefix = packKey(store.getTableDirs(1u).modelPrefix, "api-probe".encodeToByteArray())
            val range = Range.startsWith(prefix)
            store.tc.run { transaction ->
                val before = transaction.getApproximateSize().awaitResult()
                transaction.set(prefix + byteArrayOf(1), ByteArray(8_192))
                assertTrue(transaction.getApproximateSize().awaitResult() >= before + 8_192)
            }
            val splits = store.tc.run { it.getRangeSplitPoints(range, 1_024).awaitResult() }
            assertContentEquals(range.begin, splits.first())
            assertContentEquals(range.end, splits.last())
            val cancelled = runCatching {
                store.tc.run { transaction ->
                    transaction.cancel()
                    transaction.get(prefix).awaitResult()
                }
            }
            assertEquals(1025, assertIs<FDBException>(cancelled.exceptionOrNull()?.unwrapFdb()).getCode())
        } finally { store.close() }
    }

    @Test
    fun cancellingIdleWatchesReleasesTheNativeWatchBudget() = runBoundedIntegrationTest {
        val store = openStore(watchLimit = 32)
        try {
            val key = packKey(store.getTableDirs(1u).modelPrefix, "idle-watch".encodeToByteArray())
            repeat(256) {
                val watch = store.tc.run { it.watch(key) }
                watch.cancel()
            }
            // A leaked mapped Java future exhausts the budget after 32 watches.
            val final = store.tc.run { it.watch(key) }
            final.cancel()
        } finally { store.close() }
    }

    @Test
    fun watchesCanRaceCompletionCancellationAndCloseRepeatedly() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val key = packKey(store.getTableDirs(1u).modelPrefix, "watch-race".encodeToByteArray())
            repeat(500) { iteration ->
                val watch = store.tc.run { it.watch(key) }
                val writer = async { store.tc.run { it.set(key, "$iteration".encodeToByteArray()) } }
                val cancel = async { repeat(3) { watch.cancel() } }
                writer.await()
                cancel.await()
                watch.cancel()
            }
        } finally { store.close() }
    }

    private suspend fun openStore(
        root: List<String> = listOf("maryk", "robustness", Uuid.random().toString()),
        consumer: String? = null,
        execution: FoundationDBExecutionConfiguration = FoundationDBExecutionConfiguration(),
        batchSize: Int = 256,
        watchLimit: Long? = null,
    ) = FoundationDBDataStore.open(
        fdbClusterFilePath = "./fdb.cluster", directoryPath = root, dataModelsById = mapOf(1u to RobustRecord),
        transactionConfiguration = FoundationDBTransactionConfiguration(), executionConfiguration = execution,
        databaseOptionsSetter = { watchLimit?.let { setMaxWatches(it) } },
        clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
            enableClusterUpdateLog = consumer != null, clusterUpdateLogConsumerId = consumer,
            clusterUpdateLogShardCount = 2, clusterUpdateLogPollInterval = 5.seconds,
            clusterUpdateLogBatchSize = batchSize,
        ),
    )
}

internal fun injectedUnknownCommit(): FDBException =
    FDBException("Injected committed-but-unacknowledged transaction", 1021)
