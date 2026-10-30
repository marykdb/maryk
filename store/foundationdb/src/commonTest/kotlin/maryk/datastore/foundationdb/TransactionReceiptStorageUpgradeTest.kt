package maryk.datastore.foundationdb

import kotlin.random.Random
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.foundationdb.tuple.Tuple
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.TransactionStatus
import maryk.datastore.shared.AtomicReceiptCodec
import maryk.datastore.shared.tagReleasedTransactionReceipt
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private object ReceiptNanosRecord : RootDataModel<ReceiptNanosRecord>() {
    val stamps by list(1u, valueDefinition = DateTimeDefinition(precision = NANOS))
}

class TransactionReceiptStorageUpgradeTest {
    @Test
    fun releasedReceiptMatchesIdenticalRetryAfterUpgradeAndReopenWithoutRewritingChunks() = runTest {
        val directory = listOf("maryk", "released-transaction-receipt-${Random.nextInt()}")
        suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
            dataModelsById = mapOf(1u to ReceiptNanosRecord))
        var store = open()
        try {
            val values = ReceiptNanosRecord.create { stamps with listOf(LocalDateTime(1970, 1, 1, 0, 0, 8, 1)) }
            val key = ReceiptNanosRecord.key(values)
            val request = TransactionRequest("released-retry", listOf(ReceiptNanosRecord.add(key to values)), commit = true)
            val committed = store.executeTransaction(request)
            assertEquals(TransactionStatus.Committed, committed.status)
            val fingerprints = AtomicReceiptCodec(mapOf(1u to ReceiptNanosRecord)).transactionFingerprints(request)
            val released = assertNotNull(fingerprints.released)
            assertFalse(released.contentEquals(fingerprints.current))
            val prefix = store.tc.run { transaction ->
                store.rootDirectory.open(transaction, listOf("__meta__", "atomic_receipts")).awaitResult().pack()
            }
            val current = assertNotNull(store.tc.run { readAtomicReceipt(it, prefix, request.transactionId) })
            val raw = released + current.copyOfRange(tagReleasedTransactionReceipt(byteArrayOf()).size + 32, current.size)
            store.tc.run { transaction ->
                writeAtomicReceipt(transaction, prefix, request.transactionId, raw)
                transaction.clear(store.storageFormatPrefix + StorageFormatMetadataKey)
            }
            val oldChunk = store.tc.run { it.get(prefix + Tuple.from(request.transactionId, 0L).pack()).awaitResult() }
            store.close()
            store = open()
            assertContentEquals(tagReleasedTransactionReceipt(raw), store.tc.run { readAtomicReceipt(it, prefix, request.transactionId) })
            assertContentEquals(oldChunk, store.tc.run { it.get(prefix + Tuple.from(request.transactionId, 0L).pack()).awaitResult() })
            assertEquals(committed, store.executeTransaction(request))
            store.close()
            store = open()
            assertEquals(committed, store.executeTransaction(request))
            val different = ReceiptNanosRecord.create { stamps with listOf(LocalDateTime(1970, 1, 1, 0, 0, 8, 2)) }
            val mismatch = store.executeTransaction(TransactionRequest(request.transactionId, listOf(ReceiptNanosRecord.add(key to different)), commit = true))
            assertEquals(AtomicFailureCode.ChangeSetMismatch, mismatch.failure?.code)
            assertEquals(1, store.execute(ReceiptNanosRecord.scan(allowTableScan = true)).values.size)
        } finally { store.close() }
    }
}
