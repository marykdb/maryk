package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt32
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.TransactionStatus
import maryk.datastore.indexeddb.processors.createHistoricCleanupRowKey
import maryk.datastore.indexeddb.processors.createHistoricVersionedRowKey
import maryk.datastore.indexeddb.processors.createIndexRowKey
import maryk.datastore.shared.AtomicReceiptCodec
import maryk.datastore.shared.executeTransaction
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.tagReleasedTransactionReceipt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object ReceiptNanosRecord : RootDataModel<ReceiptNanosRecord>() {
    val stamp by dateTime(1u, precision = NANOS)
}

private object DescriptorLeaf : DataModel<DescriptorLeaf>() {
    val number by number(1u, type = SInt32)
}

private object DescriptorRecord : RootDataModel<DescriptorRecord>(indexes = {
    listOf(DescriptorRecord.ref { items at -1.0 }.getEmbedded("number", null) as IsIndexable)
}) {
    val items by map(1u, keyDefinition = NumberDefinition(type = Float64),
        valueDefinition = EmbeddedValuesDefinition(dataModel = { DescriptorLeaf }))
}

class ReceiptAndDescriptorUpgradeTest {
    @Test
    fun releasedReceiptRetriesDoNotReexecuteAfterUpgradeAndReopen() = runTest {
        installIndexedDbForTests()
        val database = "maryk-receipt-origin-${Random.nextInt()}"
        suspend fun open() = IndexedDbDataStore.open(database, mapOf(1u to ReceiptNanosRecord))
        var store = open()
        try {
            val key = ReceiptNanosRecord.key(ByteArray(16))
            fun request(nanos: Int) = TransactionRequest("legacy-retry", listOf(ReceiptNanosRecord.add(
                key to ReceiptNanosRecord.create { stamp with LocalDateTime(1970, 1, 1, 0, 0, 8, nanos) },
            )), commit = true)
            val original = store.executeTransaction(request(1))
            assertEquals(TransactionStatus.Committed, original.status)
            val codec = AtomicReceiptCodec(mapOf(1u to ReceiptNanosRecord))
            val fingerprints = codec.transactionFingerprints(request(1))
            val receiptKey = "maryk.transaction/legacy-retry".encodeToByteArray()
            val current = assertNotNull(store.byteStore.get("meta", receiptKey))
            val rawReleased = assertNotNull(fingerprints.released) + current.copyOfRange(
                tagReleasedTransactionReceipt(byteArrayOf()).size + fingerprints.current.size, current.size,
            )
            // Receipts remain authoritative after an ordinary later hard deletion.
            store.execute(ReceiptNanosRecord.delete(key, hardDelete = true))
            store.byteStore.writeBatch(listOf(IndexedDbWriteOperation.Put("meta", receiptKey, rawReleased),
                IndexedDbWriteOperation.Delete("meta", StorageFormatMetadataKey)))
            store.close()
            store = open()
            assertContentEquals(tagReleasedTransactionReceipt(rawReleased), store.byteStore.get("meta", receiptKey))
            assertEquals(original, store.executeTransaction(request(1)))
            assertTrue(store.execute(ReceiptNanosRecord.get(key)).values.isEmpty())
            assertEquals(AtomicFailureCode.ChangeSetMismatch, store.executeTransaction(request(2)).failure?.code)
            store.close()
            store = open()
            assertEquals(original, store.executeTransaction(request(1)))
        } finally { store.close() }
    }

    @Test
    fun fixedFloatingAncestorDescriptorMovesWithoutChangingIntegerIndexBytes() = runTest {
        installIndexedDbForTests()
        val database = "maryk-descriptor-origin-${Random.nextInt()}"
        suspend fun open() = IndexedDbDataStore.open(database, mapOf(1u to DescriptorRecord), keepAllVersions = true)
        var store = open()
        try {
            val index = DescriptorRecord.Meta.indexes!!.single()
            val key = ByteArray(16)
            val values = DescriptorRecord.create { items with mapOf(-1.0 to DescriptorLeaf.create { number with 7 }) }
            val entry = assertNotNull(index.toStorageByteArrayForIndex(values, key))
            // Independent released descriptor: fixed -1.0 key c010... beneath property 1.
            val releasedDescriptor = "5a0c08c01000000000000009".hexToByteArray()
            val oldRow = createIndexRowKey(releasedDescriptor, entry)
            val oldHistoric = createHistoricVersionedRowKey(oldRow, 10uL)
            val oldCleanup = createHistoricCleanupRowKey(key, oldHistoric)
            store.byteStore.writeBatch(listOf(
                IndexedDbWriteOperation.Put("i:1", oldRow, byteArrayOf()),
                IndexedDbWriteOperation.Put("hi:1", oldHistoric, byteArrayOf(1)),
                IndexedDbWriteOperation.Put("hik:1", oldCleanup, oldHistoric),
                IndexedDbWriteOperation.Delete("meta", StorageFormatMetadataKey),
            ))
            store.close()
            store = open()
            val newRow = createIndexRowKey(index.referenceStorageByteArray.bytes, entry)
            val newHistoric = createHistoricVersionedRowKey(newRow, 10uL)
            assertNull(store.byteStore.get("i:1", oldRow))
            assertContentEquals(byteArrayOf(), store.byteStore.get("i:1", newRow))
            assertContentEquals(byteArrayOf(1), store.byteStore.get("hi:1", newHistoric))
            assertContentEquals(newHistoric, store.byteStore.get("hik:1", createHistoricCleanupRowKey(key, newHistoric)))
            store.close()
            store = open()
            assertContentEquals(byteArrayOf(), store.byteStore.get("i:1", newRow))
        } finally { store.close() }
    }
}
