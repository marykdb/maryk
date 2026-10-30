package maryk.datastore.rocksdb

import maryk.createTestDBFolder
import maryk.deleteFolder
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
    fun releasedReceiptMatchesIdenticalRetryAfterUpgradeAndReopen() = runTest {
        val folder = createTestDBFolder("released-transaction-receipt")
        suspend fun open() = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to ReceiptNanosRecord))
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
            val receiptKey = TransactionReceiptPrefix + request.transactionId.encodeToByteArray()
            val current = assertNotNull(store.db.get(store.transactionMetadataHandle, receiptKey))
            // Released storage has no envelope and hashes the actual released NANOS request codec.
            val raw = released + current.copyOfRange(tagReleasedTransactionReceipt(byteArrayOf()).size + 32, current.size)
            val legacyNonce = ByteArray(16) { (it + 7).toByte() }
            store.db.put(store.transactionMetadataHandle, receiptKey, raw)
            store.db.put(store.transactionMetadataHandle, LegacyTransactionRevisionKey, legacyNonce)
            store.db.delete(store.transactionMetadataHandle, TransactionRevisionKey)
            store.db.delete(store.transactionMetadataHandle, StorageFormatMetadataKey)
            store.close()
            store = open()
            assertContentEquals(tagReleasedTransactionReceipt(raw), store.db.get(store.transactionMetadataHandle, receiptKey))
            assertContentEquals(legacyNonce, store.db.get(store.transactionMetadataHandle, TransactionRevisionKey))
            assertNull(store.db.get(store.transactionMetadataHandle, LegacyTransactionRevisionKey))
            assertEquals(committed, store.executeTransaction(request))
            store.close()
            store = open()
            assertEquals(committed, store.executeTransaction(request))
            val different = ReceiptNanosRecord.create { stamps with listOf(LocalDateTime(1970, 1, 1, 0, 0, 8, 2)) }
            val mismatch = store.executeTransaction(TransactionRequest(request.transactionId, listOf(ReceiptNanosRecord.add(key to different)), commit = true))
            assertEquals(AtomicFailureCode.ChangeSetMismatch, mismatch.failure?.code)
            assertEquals(1, store.execute(ReceiptNanosRecord.scan(allowTableScan = true)).values.size)
        } finally { store.close(); deleteFolder(folder) }
    }

    @Test
    fun transactionNamedRevisionKeepsItsReceiptAfterOtherMutations() = runTest {
        val folder = createTestDBFolder("transaction-id-revision")
        suspend fun open() = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to ReceiptNanosRecord))
        var store = open()
        try {
            val first = ReceiptNanosRecord.create { stamps with listOf(LocalDateTime(1970, 1, 1, 0, 0, 8, 1)) }
            val request = TransactionRequest("revision", listOf(ReceiptNanosRecord.add(ReceiptNanosRecord.key(first) to first)), commit = true)
            val committed = store.executeTransaction(request)
            assertEquals(TransactionStatus.Committed, committed.status)
            store.execute(ReceiptNanosRecord.add(ReceiptNanosRecord.create { stamps with emptyList() }))
            assertEquals(committed, store.executeTransaction(request))
            store.close()
            store = open()
            assertEquals(committed, store.executeTransaction(request))
            assertEquals(2, store.execute(ReceiptNanosRecord.scan(allowTableScan = true)).values.size)
        } finally { store.close(); deleteFolder(folder) }
    }
}
