package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.foundationdb.model.modelHlcWatermarkPrefix
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.unwrapFdb
import maryk.foundationdb.FDBException
import maryk.foundationdb.Range
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class FoundationDBApiImprovementsTest {
    @Test
    fun atomicWatermarksKeepUnsignedMaximumWithoutReadConflicts() = runTest {
        val store = openStore()
        try {
            val dirs = store.getTableDirs(1u)
            val recordKey = byteArrayOf(42)
            val high = HLC(0x8000000000000000uL)
            val low = HLC(0x7FFFFFFFFFFFFFFFuL)
            var attempts = 0
            store.tc.run { outer ->
                attempts++
                store.persistDurableClockWatermark(outer, dirs, recordKey, high)
                store.tc.run { inner -> store.persistDurableClockWatermark(inner, dirs, recordKey, low) }
            }
            assertEquals(1, attempts, "Concurrent writes to one watermark bucket must not trigger a retry")
            val rows = store.tc.run { transaction ->
                transaction.getRange(Range.startsWith(packKey(dirs.modelPrefix, modelHlcWatermarkPrefix))).asList().awaitResult()
            }
            val buckets = rows.filter { it.key.size == dirs.modelPrefix.size + 3 }
            assertEquals(1, buckets.size)
            assertContentEquals(HLC.toStorageBytes(high), buckets.single().value)
            store.tc.run { transaction -> store.persistDurableClockWatermark(transaction, dirs, recordKey, low) }
            val retained = store.tc.run { it.get(buckets.single().key).awaitResult() }
            assertContentEquals(HLC.toStorageBytes(high), retained)
        } finally { store.close() }
    }

    @Test
    fun revisionReadOnlyConflictsWhenReplayRequiresIt() = runTest {
        val store = openStore()
        try {
            for (requireConflict in listOf(false, true)) {
                var attempts = 0
                val writeKey = packKey(store.getTableDirs(1u).modelPrefix, "revision-test".encodeToByteArray())
                store.tc.run { transaction ->
                    attempts++
                    transaction.readTransactionRevision(store.transactionRevisionKey, requireConflict)
                    if (attempts == 1) store.tc.run { competitor -> store.advanceTransactionRevision(competitor) }
                    transaction.set(writeKey, byteArrayOf(attempts.toByte()))
                }
                assertEquals(if (requireConflict) 2 else 1, attempts)
            }
        } finally { store.close() }
    }

    @Test
    fun requestAndMaintenanceSizeLimitsAreIndependent() = runTest {
        val store = openStore(FoundationDBTransactionConfiguration(
            requestPolicy = FoundationDBTransactionPolicy(sizeLimitBytes = 1_024),
        ))
        try {
            val key = packKey(store.getTableDirs(1u).modelPrefix, "policy-size".encodeToByteArray())
            val error = runCatching {
                store.tc.runConfigured(FoundationDBTransactionContext(FoundationDBTransactionKind.Request)) {
                    it.set(key, ByteArray(2_048))
                }
            }
            assertEquals(2101, assertIs<FDBException>(error.exceptionOrNull()?.unwrapFdb()).getCode())
            store.tc.run { it.set(key, ByteArray(2_048)) }
            assertEquals(2_048, store.tc.run { it.get(key).awaitResult() }?.size)
        } finally { store.close() }
    }

    @Test
    fun databaseOverridesRetainPrecedenceAndHooksCanSpecializeRequests() = runTest {
        val lowerLimit = atomic(false)
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "policy-precedence", Uuid.random().toString()),
            dataModelsById = mapOf(1u to SimpleMarykModel),
            databaseOptionsSetter = { setTransactionSizeLimit(32_768) },
            transactionConfiguration = FoundationDBTransactionConfiguration(
                requestPolicy = FoundationDBTransactionPolicy(sizeLimitBytes = 1_024),
                maintenancePolicy = FoundationDBTransactionPolicy(sizeLimitBytes = 16_384),
                optionsSetter = { context ->
                    if (context.kind == FoundationDBTransactionKind.Request && lowerLimit.value) setSizeLimit(1_024)
                },
            ),
        )
        try {
            val key = packKey(store.getTableDirs(1u).modelPrefix, "precedence".encodeToByteArray())
            val request = FoundationDBTransactionContext(FoundationDBTransactionKind.Request)
            // The existing database override wins over the request policy.
            store.tc.runConfigured(request) { it.set(key, ByteArray(20_000)) }
            // The maintenance policy wins over the database request default.
            val maintenance = runCatching { store.tc.run { it.set(key, ByteArray(20_000)) } }
            assertEquals(2101, assertIs<FDBException>(maintenance.exceptionOrNull()?.unwrapFdb()).getCode())
            lowerLimit.value = true
            // Finally, the hook can specialize an individual operation.
            val specialized = runCatching { store.tc.runConfigured(request) { it.set(key, ByteArray(20_000)) } }
            assertEquals(2101, assertIs<FDBException>(specialized.exceptionOrNull()?.unwrapFdb()).getCode())
        } finally { store.close() }
    }

    @Test
    fun requestTimeoutDoesNotShortenMaintenancePolicy() = runTest(timeout = 3.minutes) {
        val store = openStore(FoundationDBTransactionConfiguration(
            requestPolicy = FoundationDBTransactionPolicy(timeoutMillis = 50),
        ))
        try {
            val key = store.transactionRevisionKey
            val error = runCatching {
                store.tc.runConfigured(FoundationDBTransactionContext(FoundationDBTransactionKind.Request)) { transaction ->
                    transaction.getReadVersion().awaitResult()
                    runBlocking { delay(150) }
                    transaction.get(key).awaitResult()
                }
            }
            assertEquals(1031, assertIs<FDBException>(error.exceptionOrNull()?.unwrapFdb()).getCode())
            store.tc.run { transaction ->
                transaction.getReadVersion().awaitResult()
                runBlocking { delay(150) }
                transaction.get(key).awaitResult()
            }
        } finally { store.close() }
    }

    @Test
    fun diagnosticHookObservesRealRequestsAndRetries() = runTest {
        val contexts = atomic<List<FoundationDBTransactionContext>>(emptyList())
        val store = openStore(FoundationDBTransactionConfiguration(optionsSetter = { context ->
            contexts.update { it + context }
            setTag(context.kind.name)
            setDebugTransactionIdentifier("maryk-${context.kind.name}")
            setLogTransaction()
        }))
        try {
            contexts.value = emptyList()
            val added = assertIs<AddSuccess<SimpleMarykModel>>(store.execute(SimpleMarykModel.add(
                SimpleMarykModel.create { value with "happy diagnostics" },
            )).statuses.single())
            store.execute(SimpleMarykModel.get(added.key))
            assertTrue(contexts.value.any { it.kind == FoundationDBTransactionKind.Request && it.dataModelId == 1u })
            assertTrue(contexts.value.any { it.kind == FoundationDBTransactionKind.Read && it.dataModelId == 1u })
            contexts.value = emptyList()
            var attempts = 0
            val key = packKey(store.getTableDirs(1u).modelPrefix, "hook-retry".encodeToByteArray())
            store.tc.run { transaction ->
                attempts++
                transaction.get(key).awaitResult()
                if (attempts == 1) store.tc.run { it.set(key, byteArrayOf(1)) }
                transaction.set(key, byteArrayOf(2))
            }
            assertEquals(2, attempts)
            assertEquals(3, contexts.value.count { it.kind == FoundationDBTransactionKind.Maintenance })
        } finally { store.close() }
    }

    private suspend fun openStore(configuration: FoundationDBTransactionConfiguration = FoundationDBTransactionConfiguration()) =
        FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "api-improvements", Uuid.random().toString()),
            dataModelsById = mapOf(1u to SimpleMarykModel),
            transactionConfiguration = configuration,
        )
}
