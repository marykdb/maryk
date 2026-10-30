package maryk.datastore.shared

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.models.RootDataModel
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.get
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.ValuesResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object ClockRangeRecord : RootDataModel<ClockRangeRecord>()

class TransactionVersionRangeTest {
    private val key = Key<ClockRangeRecord>(ByteArray(16))

    @Test
    fun overflowingRangeRejectsBeforeApplyingAnyMember() = runTest {
        for (commit in listOf(false, true)) {
            val request = TransactionRequest("overflow-$commit", List(3) { ClockRangeRecord.get(key) }, commit = commit)
            var applied = 0
            val abort = assertFailsWith<TransactionAbort> {
                applyTransactionMembers(request, Bytes(byteArrayOf(0)), HLC(ULong.MAX_VALUE - 2uL)) { _, _ ->
                    applied++
                    ValuesResponse(ClockRangeRecord, emptyList())
                }
            }
            assertEquals(TransactionStatus.Aborted, abort.response.status)
            assertEquals(AtomicFailureCode.InvalidState, abort.response.failure?.code)
            assertEquals(0, applied)
        }
    }

    @Test
    fun lastRepresentableRangeKeepsEveryOperationOrdered() = runTest {
        val request = TransactionRequest("last-range", List(3) { ClockRangeRecord.get(key) })
        val versions = mutableListOf<ULong>()
        val response = applyTransactionMembers(request, Bytes(byteArrayOf(0)), HLC(ULong.MAX_VALUE - 3uL)) { _, version ->
            versions += version.timestamp
            ValuesResponse(ClockRangeRecord, emptyList())
        }
        assertEquals(listOf(ULong.MAX_VALUE - 2uL, ULong.MAX_VALUE - 1uL, ULong.MAX_VALUE), versions)
        assertEquals(ULong.MAX_VALUE, response.version)
    }
}
