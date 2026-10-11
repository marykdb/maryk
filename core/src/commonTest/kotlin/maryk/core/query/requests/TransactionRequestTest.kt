package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.key
import maryk.core.exceptions.RequestException
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Bytes
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.responses.AtomicFailure
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionRequestTest {
    private fun context() = RequestContext(mapOf(SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)))
    private val add = TransactionRequest("keys", listOf(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-tx" }))).requests.single() as AddRequest<*>
    private val key = add.keysForObjects!!.single()
    private val request = TransactionRequest("journal", listOf(add, SimpleMarykModel.get(SimpleMarykModel.key(key.bytes)),
        SimpleMarykModel.change(SimpleMarykModel.key(key.bytes).change(Change(SimpleMarykModel.ref { value } with "haha-next"))),
        SimpleMarykModel.delete(SimpleMarykModel.key(key.bytes), hardDelete = true), SimpleMarykModel.scan(allowTableScan = true)), Bytes(byteArrayOf(1, 2)), 42uL, true)

    private fun compare(converted: TransactionRequest, original: TransactionRequest) {
        assertEquals(original.transactionId, converted.transactionId)
        assertEquals(original.requests, converted.requests)
        assertEquals(original.expectedRevision, converted.expectedRevision)
        assertEquals(original.stagingVersion, converted.stagingVersion)
        assertEquals(original.commit, converted.commit)
        assertEquals(original.readOnly, converted.readOnly)
        assertEquals(original.initialGuards, converted.initialGuards)
        assertEquals(original.initialStateChecks, converted.initialStateChecks)
    }

    @Test fun requestRoundTrips() {
        checkProtoBufConversion(request, TransactionRequest, ::context, ::compare)
        checkJsonConversion(request, TransactionRequest, ::context, ::compare)
        checkYamlConversion(request, TransactionRequest, ::context, ::compare)
        checkProtoBufConversion(TransactionRequest("read-only", readOnly = true), TransactionRequest, ::context, ::compare)
        val guarded = TransactionRequest("guarded", listOf(request.requests[2]), initialGuards = listOf(
            SimpleMarykModel.change(SimpleMarykModel.key(key.bytes).change(Check(SimpleMarykModel.ref { value } with "haha-tx"))),
        ), initialStateChecks = true, commit = true)
        checkProtoBufConversion(guarded, TransactionRequest, ::context, ::compare)
        checkJsonConversion(guarded, TransactionRequest, ::context, ::compare)
        checkYamlConversion(guarded, TransactionRequest, ::context, ::compare)
    }
    @Test fun responseAndCapabilitiesRoundTrip() {
        val response = TransactionResponse(TransactionStatus.Aborted, revision = Bytes(byteArrayOf(1)), version = 42uL,
            failedRequestIndex = 2u, failure = AtomicFailure(AtomicFailureCode.Conflict))
        checkProtoBufConversion(response, TransactionResponse, ::context)
        checkJsonConversion(response, TransactionResponse, ::context)
        checkYamlConversion(response, TransactionResponse, ::context)
        checkProtoBufConversion(TransactionCapabilities(AtomicCapabilities()), TransactionCapabilities, ::context)
        checkJsonConversion(TransactionCapabilities(AtomicCapabilities()), TransactionCapabilities, ::context)
    }
    @Test fun rejectsUnboundedAndReadOnlyWrites() {
        assertFailsWith<RequestException> { TransactionRequest("too-many", List(2001) { request.requests[1] }) }
        assertFailsWith<RequestException> { TransactionRequest("read-only", listOf(add), readOnly = true) }
    }
    @Test fun rejectsInvalidGuardModesAndOverflowingVersions() {
        val guard = SimpleMarykModel.change(SimpleMarykModel.key(key.bytes).change())
        assertFailsWith<RequestException> { TransactionRequest("ignored-guard", initialGuards = listOf(guard)) }
        assertFailsWith<RequestException> { TransactionRequest("invalid-atomic", listOf(request.requests[1]), initialStateChecks = true) }
        assertFailsWith<RequestException> { TransactionRequest("overflow", listOf(add), stagingVersion = ULong.MAX_VALUE) }
        val readonlyCheck = TransactionRequest("readonly-check", listOf(guard), readOnly = true)
        assertEquals(false, readonlyCheck.hasMutations)
        assertEquals(100_000u, TransactionCapabilities(AtomicCapabilities()).maxReadKeys)
        assertEquals(100_000u, TransactionCapabilities(AtomicCapabilities()).maxReadRows)
    }
}
