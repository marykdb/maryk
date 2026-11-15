package maryk.datastore.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.datastore.foundationdb.processors.helpers.PreparedRecordRead
import maryk.datastore.foundationdb.processors.helpers.PreparedRangeRead
import maryk.datastore.foundationdb.processors.helpers.readStoredValue
import maryk.datastore.shared.RestoreVersionRequest
import maryk.datastore.shared.TransactionException
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.beginTransaction
import maryk.datastore.shared.readValue
import maryk.foundationdb.FutureCancellation
import maryk.foundationdb.KeyValue
import maryk.foundationdb.asFdbFuture
import maryk.foundationdb.completedFdbFuture
import maryk.foundationdb.async.AsyncIterator
import maryk.test.models.SimpleMarykModel

class FoundationDBReadMemoryTest {
    @Test fun deferredSnapshotPinsOnceAndRejectsAnotherVersion() {
        var captures = 0
        val context = FoundationDBReadContext(
            readBudget = FoundationDBReadBudget(8192),
            readVersionSource = { captures++; 42L },
        )
        assertEquals(null, context.pinnedReadVersion)
        context.captureSnapshot()
        context.captureSnapshot()
        assertEquals(42L, context.readVersion)
        assertEquals(1, captures)
        context.pinReadVersion(42L)
        assertFailsWith<IllegalStateException> { context.pinReadVersion(43L) }
    }

    @Test fun firstReadCanSupplyTheSnapshotWithoutAnotherNativeCapture() {
        val context = FoundationDBReadContext(
            readBudget = FoundationDBReadBudget(8192),
            readVersionSource = { error("Should use the first read transaction") },
        )
        context.pinReadVersion(42L)
        context.captureSnapshot()
        assertEquals(42L, context.readVersion)
    }

    @Test fun actorGetsUseOneNativeSnapshotWhileExplicitAndReusableContextsStillPin() = runBlocking<Unit> {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "single-transaction-snapshot", Uuid.random().toString()),
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = false,
        )
        try {
            val singleUse = store.createReadContext(deferSnapshot = true, retainImplicitSnapshot = false)
            val nativeVersion = store.runReadTransactionSuspending(singleUse, 1u) { it.getReadVersion().await() }
            assertEquals(null, singleUse.pinnedReadVersion)

            val explicit = store.createReadContext(deferSnapshot = true, retainImplicitSnapshot = false)
            explicit.captureSnapshot()
            val pinned = explicit.pinnedReadVersion
            assertTrue(requireNotNull(pinned) >= nativeVersion)
            assertEquals(pinned, store.runReadTransactionSuspending(explicit, 1u) { it.getReadVersion().await() })
            assertEquals(pinned, explicit.pinnedReadVersion)

            val reusable = store.createReadContext(deferSnapshot = true)
            val first = store.runReadTransactionSuspending(reusable, 1u) { it.getReadVersion().await() }
            assertEquals(first, reusable.pinnedReadVersion)
            assertEquals(first, store.runReadTransactionSuspending(reusable, 1u) { it.getReadVersion().await() })
        } finally { store.close() }
    }

    @Test fun repeatedRecordsStillRespectTheRequestMaterializationBudget() = runBlocking<Unit> {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "memo-budget", Uuid.random().toString()),
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = false,
            transactionConfiguration = FoundationDBTransactionConfiguration(),
            executionConfiguration = FoundationDBExecutionConfiguration(
                maxRecordReadBytes = 8192, maxRequestReadBytes = 16384,
            ),
        )
        try {
            val text = "ha" + "x".repeat(4094)
            val added = store.execute(SimpleMarykModel.add(
                SimpleMarykModel.create { value with text },
            )).statuses.single() as AddSuccess<SimpleMarykModel>
            assertEquals(text, store.execute(SimpleMarykModel.get(added.key)).values.single().values { value })
            assertFailsWith<FoundationDBReadLimitExceededException> {
                store.execute(SimpleMarykModel.get(*Array(8) { added.key }))
            }
        } finally {
            store.close()
        }
    }

    @Test fun transactionPreviewsUseTheConfiguredRequestBudget() = runBlocking<Unit> {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "transaction-budget", Uuid.random().toString()),
            dataModelsById = mapOf(1u to SimpleMarykModel), keepAllVersions = false,
            transactionConfiguration = FoundationDBTransactionConfiguration(),
            executionConfiguration = FoundationDBExecutionConfiguration(
                maxRecordReadBytes = 8192, maxRequestReadBytes = 16384,
            ),
        )
        try {
            val added = store.execute(SimpleMarykModel.add(
                SimpleMarykModel.create { value with ("ha" + "x".repeat(4094)) },
            )).statuses.single() as AddSuccess<SimpleMarykModel>
            val request = SimpleMarykModel.get(*Array(8) { added.key })
            assertFailsWith<FoundationDBReadLimitExceededException> { store.execute(request) }
            val transaction = store.beginTransaction()
            try {
                val failure = assertFailsWith<TransactionException> { transaction.execute(request) }
                assertEquals(TransactionStatus.Aborted, failure.response.status)
                assertTrue(failure.message.orEmpty().contains("16384-byte"))
            } finally { transaction.rollback() }
            // A new attempt has a new budget rather than inheriting the failed charge.
            val fresh = store.beginTransaction()
            try {
                assertEquals(1, fresh.execute(SimpleMarykModel.get(added.key)).response.values.size)
            } finally { fresh.rollback() }
            val commit = store.executeTransaction(TransactionRequest(Uuid.random().toString(), listOf(request), commit = true))
            assertTrue(commit.status == TransactionStatus.Aborted || commit.status == TransactionStatus.OutcomeUnknown)
            assertTrue(commit.message.orEmpty().contains("16384-byte"))
        } finally { store.close() }
    }

    @Test fun restoresUseTheConfiguredRequestBudgetWithoutPartialWrites() = runBlocking<Unit> {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "restore-budget", Uuid.random().toString()),
            dataModelsById = mapOf(1u to SimpleMarykModel), keepAllVersions = false,
            transactionConfiguration = FoundationDBTransactionConfiguration(),
            executionConfiguration = FoundationDBExecutionConfiguration(
                maxRecordReadBytes = 8192, maxRequestReadBytes = 16384,
            ),
        )
        try {
            val text = "ha" + "x".repeat(4094)
            val added = store.execute(SimpleMarykModel.add(*Array(8) {
                SimpleMarykModel.create { value with text }
            })).statuses.map { it as AddSuccess<SimpleMarykModel> }
            val restoreVersion = added.maxOf { it.version } + 1uL
            fun restore(keys: List<Key<SimpleMarykModel>>) = RestoreVersionRequest(listOf(
                UpdateResponse<IsRootDataModel>(SimpleMarykModel, InitialChangesUpdate(restoreVersion + 1uL,
                    keys.map { key -> DataObjectVersionedChange<IsRootDataModel>(Key(key.bytes), changes = listOf(
                        VersionedChanges(restoreVersion, listOf(Change(SimpleMarykModel.ref { value } with "ha-restored"))),
                    )) },
                )),
            ))
            assertFailsWith<FoundationDBReadLimitExceededException> {
                store.restoreVersion(restore(added.map { it.key }))
            }
            for (record in added) {
                assertEquals(text, store.execute(SimpleMarykModel.get(record.key)).values.single().values { value })
            }
            store.restoreVersion(restore(listOf(added.first().key)))
            assertEquals("ha-restored", store.execute(SimpleMarykModel.get(added.first().key)).values.single().values { value })
        } finally { store.close() }
    }

    @Test fun boundedStringDecodingMatchesGeneralStorageDecoder() {
        val definition = StringDefinition()
        for (text in listOf("", "héllo\u0000世界🙂", "x".repeat(65_536))) {
            val payload = byteArrayOf(TypeIndicator.NoTypeIndicator.byte) + text.encodeToByteArray()
            val wrapped = ByteArray(7) + payload + ByteArray(9)
            var index = 7
            val expected = readValue(definition, { wrapped[index++] }) { 7 + payload.size - index }
            assertEquals(expected, readStoredValue(definition, wrapped, 7, payload.size))
            assertEquals(text, expected)
        }
        assertEquals(null, readStoredValue(definition, byteArrayOf(TypeIndicator.DeletedIndicator.byte), 0, 1))
        assertFails { readStoredValue(definition, byteArrayOf(1, 0xc3.toByte(), 0x28), 0, 3) }
        assertFails { readStoredValue(definition, byteArrayOf(1,2,3), 0, 0) }
        assertFails { readStoredValue(definition, byteArrayOf(1), Int.MAX_VALUE, 1) }
    }

    @Test fun releaseDropsPreparedRowsAndRejectsAccidentalReuse() = runBlocking<Unit> {
        val rows = listOf(KeyValue(byteArrayOf(1), byteArrayOf(2)))
        val read = PreparedRecordRead(emptyList(), listOf(preparedRange(rows)), FoundationDBReadBudget(1024))
        assertEquals(rows,read.awaitRowsSuspending())
        read.cancel()
        assertEquals(rows,read.awaitRowsSuspending())
        read.release(); read.release()
        assertFailsWith<IllegalStateException> { read.awaitRowsSuspending() }
    }

    @Test fun releasingUnconsumedReadCancelsOwnedWork() = runBlocking {
        val ready = CompletableDeferred<Boolean>()
        val read = PreparedRecordRead(emptyList(), listOf(preparedRange(emptyList(), ready)), FoundationDBReadBudget(1024))
        read.release()
        assertTrue(ready.isCancelled)
    }

    private fun preparedRange(rows: List<KeyValue>, ready: CompletableDeferred<Boolean>? = null): PreparedRangeRead {
        var index = 0
        return PreparedRangeRead(object : AsyncIterator<KeyValue> {
            override fun hasNext() = index < rows.size
            override fun onHasNext() = ready?.asFdbFuture(FutureCancellation.PROPAGATE) ?: completedFdbFuture(hasNext())
            override suspend fun next() = rows[index++]
            override fun cancel() { ready?.cancel() }
        }, FoundationDBReadBudget(1024))
    }
}
