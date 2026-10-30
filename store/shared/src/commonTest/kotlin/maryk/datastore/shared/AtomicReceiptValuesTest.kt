package maryk.datastore.shared

import kotlinx.coroutines.test.runTest
import maryk.core.models.key
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.change
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.change
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AtomicReceiptValuesTest {
    @Test
    fun durableReceiptOmitsGeneratedValuesAndRestoresEachExactOperation() = runTest {
        val first = "request-only-secret-first-739214"
        val second = "request-only-secret-second-532194"
        val reference = TestMarykModel.ref { incMap }
        val request = TransactionRequest("private-inc-map-receipt", listOf(TestMarykModel.change(
            TestMarykModel.key(ByteArray(TestMarykModel.Meta.keyByteSize)).change(
                IncMapChange(reference.change(addValues = listOf(first))),
                IncMapChange(reference.change(addValues = listOf(second))),
            ),
        )))
        val response = TransactionResponse(TransactionStatus.Committed, listOf(ChangeResponse(TestMarykModel,
            listOf(ChangeSuccess<TestMarykModel>(100uL, listOf(IncMapAddition(
                IncMapKeyAdditions(reference, listOf(42u), listOf(first)),
                IncMapKeyAdditions(reference, listOf(41u), listOf(second)),
            )))),
        )))
        val codec = AtomicReceiptCodec(mapOf(1u to TestMarykModel))
        val fingerprint = codec.transactionFingerprint(request)
        val receipt = codec.transactionReceipt(fingerprint, response)
        assertFalse(receipt.decodeToString().contains(first))
        assertFalse(receipt.decodeToString().contains(second))
        val replay = codec.replayTransaction(receipt, fingerprint, request)
        val status = (replay.responses.single() as ChangeResponse<*>).statuses.single() as ChangeSuccess<*>
        val additions = status.changes!!.filterIsInstance<IncMapAddition>().single().additions
        assertEquals(listOf(first), additions[0].addedValues)
        assertEquals(listOf(second), additions[1].addedValues)
    }
}
