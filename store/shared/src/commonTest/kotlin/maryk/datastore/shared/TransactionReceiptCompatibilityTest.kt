package maryk.datastore.shared

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.ValueDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.valueObject
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.Float64
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.add
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.values.ObjectValues
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private class ReceiptFingerprintValue(val number: Double) : ValueDataObject(toBytes(number)) {
    companion object : ValueDataModel<ReceiptFingerprintValue, Companion>(ReceiptFingerprintValue::class) {
        val number by number(1u, ReceiptFingerprintValue::number, type = Float64)
        override fun invoke(values: ObjectValues<ReceiptFingerprintValue, Companion>) = ReceiptFingerprintValue(values(1u))
    }
}

private object ReceiptCompatibilityRecord : RootDataModel<ReceiptCompatibilityRecord>() {
    val packed by valueObject(1u, dataModel = ReceiptFingerprintValue)
}

class TransactionReceiptCompatibilityTest {
    @Test
    fun releasedOpaqueValueRequestRetainsItsCommittedReceiptIdentity() = runTest {
        val key = ReceiptCompatibilityRecord.key(ByteArray(16))
        fun request(number: Double) = TransactionRequest("released-receipt", listOf(ReceiptCompatibilityRecord.add(
            key to ReceiptCompatibilityRecord.create { packed with ReceiptFingerprintValue(number) },
        )), commit = true)
        // Independent released protobuf framing and released -1.0 value-object bytes.
        val oldPacked = byteArrayOf(0xc0.toByte(), 0x10, 0, 0, 0, 0, 0, 0)
        val add = field(1, "ReceiptCompatibilityRecord".encodeToByteArray()) + field(2, field(1, oldPacked)) + field(3, ByteArray(16))
        val oldRequest = field(1, "released-receipt".encodeToByteArray()) + field(2, field(1, add)) +
            byteArrayOf(0x28, 1) + field(32768 + 6, byteArrayOf()) + byteArrayOf(0x38, 0, 0x40, 0)
        val oldHash = CryptographyProvider.Default.get(SHA256).hasher().hash(oldRequest)
        val codec = AtomicReceiptCodec(mapOf(1u to ReceiptCompatibilityRecord))
        val fingerprints = codec.transactionFingerprints(request(-1.0))
        assertFalse(fingerprints.current.contentEquals(oldHash))
        assertContentEquals(oldHash, fingerprints.released)
        val response = TransactionResponse(TransactionStatus.Committed,
            listOf(AddResponse(ReceiptCompatibilityRecord, listOf(AddSuccess(key, 10uL, emptyList())))))
        val raw = oldHash + encodeResponse(response)
        val receipt = tagReleasedTransactionReceipt(raw)
        val replay = codec.replayTransaction(receipt, fingerprints, request(-1.0))
        assertEquals(response, replay)
        val mismatch = codec.replayTransaction(receipt, codec.transactionFingerprints(request(-2.0)), request(-2.0))
        assertEquals(AtomicFailureCode.ChangeSetMismatch, mismatch.failure?.code)
        // A digest at a different codec boundary cannot establish an ambiguous legacy identity.
        val unsupported = codec.replayTransaction(receipt,
            TransactionFingerprints(oldHash, null, "Legacy collection element boundaries are ambiguous"), request(-1.0))
        assertEquals(AtomicFailureCode.InvalidState, unsupported.failure?.code)
    }

    private fun encodeResponse(response: TransactionResponse): ByteArray {
        val context = RequestContext(DefinitionsContext(mutableMapOf(
            ReceiptCompatibilityRecord.Meta.name to DataModelReference(ReceiptCompatibilityRecord),
        )))
        val cache = WriteCache()
        val bytes = ByteArray(TransactionResponse.Serializer.calculateObjectProtoBufLength(response, cache, context))
        var offset = 0
        TransactionResponse.Serializer.writeObjectProtoBuf(response, cache, { bytes[offset++] = it }, context)
        return bytes
    }

    private fun field(tag: Int, payload: ByteArray) = varint((tag shl 3) or 2) + varint(payload.size) + payload
    private fun varint(value: Int): ByteArray = buildList {
        var remaining = value
        while (remaining > 127) { add(((remaining and 127) or 128).toByte()); remaining = remaining ushr 7 }
        add(remaining.toByte())
    }.toByteArray()
}
