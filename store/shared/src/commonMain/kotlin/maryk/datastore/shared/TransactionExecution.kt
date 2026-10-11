package maryk.datastore.shared

import maryk.core.clock.HLC
import maryk.core.models.RootDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Bytes
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.changes.change
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.AtomicFailure
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.AtomicResponse
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.IsDataModelResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.requests.MAX_ATOMIC_OPERATIONS
import maryk.core.query.requests.MAX_TRANSACTION_READ_ROWS
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.StatusType

internal object EmptyTransactionAnchor : RootDataModel<EmptyTransactionAnchor>()

internal class TransactionStoreRequest(val transaction: TransactionRequest, override val dataModel: IsRootDataModel) :
    IsStoreRequest<IsRootDataModel, TransactionResponse>

class TransactionAbort(val response: TransactionResponse) : RuntimeException(response.message)

fun checkTransactionRevision(request: TransactionRequest, revision: Bytes) {
    if (request.expectedRevision != null && request.expectedRevision != revision) throw TransactionAbort(TransactionResponse(
        TransactionStatus.Aborted, message = "The datastore changed during this transaction",
        failure = AtomicFailure(AtomicFailureCode.Conflict),
    ))
}

fun AtomicResponse.asTransactionResponse() = TransactionResponse(
    when (status) {
        AtomicStatus.Committed -> TransactionStatus.Committed
        AtomicStatus.OutcomeUnknown -> TransactionStatus.OutcomeUnknown
        AtomicStatus.Unsupported -> TransactionStatus.Unsupported
        AtomicStatus.Staged -> TransactionStatus.Staged
        AtomicStatus.Rejected -> TransactionStatus.Aborted
    }, responses, failedRequestIndex = failedRequestIndex, message = message, failure = failure, failedGuard = failedGuard,
)

fun TransactionResponse.asAtomicResponse() = AtomicResponse(
    when (status) {
        TransactionStatus.Committed -> AtomicStatus.Committed
        TransactionStatus.OutcomeUnknown -> AtomicStatus.OutcomeUnknown
        TransactionStatus.Unsupported -> AtomicStatus.Unsupported
        TransactionStatus.Staged -> AtomicStatus.Staged
        TransactionStatus.Aborted -> AtomicStatus.Rejected
    }, responses, failedRequestIndex = failedRequestIndex, message = message, failure = failure, failedGuard = failedGuard,
)

/** Ordered reads and writes execute in the backend's private staging boundary. */
suspend fun applyTransactionMembers(
    request: TransactionRequest,
    revision: Bytes,
    version: HLC,
    apply: suspend (IsStoreRequest<IsRootDataModel, IsResponse>, HLC) -> IsDataModelResponse<*>,
): TransactionResponse {
    checkTransactionRevision(request, revision)
    val count = request.requests.sumOf {
        when (it) {
            is AddRequest<*> -> it.objects.size
            is ChangeRequest<*> -> it.objects.count { obj -> obj.changes.any { change -> change !is Check } }
            is DeleteRequest<*> -> it.keys.size
            else -> 0
        }
    }
    if (count.toUInt() > MAX_ATOMIC_OPERATIONS) rejectAtomicLimit("mutations", MAX_ATOMIC_OPERATIONS.toULong(), count.toULong())
    if (request.initialStateChecks) {
        val response = applyAtomicMembers(AtomicRequest(request.transactionId, request.requests, request.initialGuards)) { apply(it, version) }
        return response.asTransactionResponse().copy(status = if (request.commit) TransactionStatus.Committed else TransactionStatus.Staged,
            revision = revision, version = version.timestamp)
    }
    val responses = mutableListOf<IsDataModelResponse<*>>()
    val modified = mutableMapOf<Pair<String, Bytes>, Pair<ULong, ULong>>()
    var rows = 0L
    for ((index, original) in request.requests.withIndex()) {
        val operationVersion = HLC(version.timestamp + index.toULong() + 1uL)
        val provisionalVersion = request.stagingVersion?.let { it + index.toULong() + 1uL } ?: operationVersion.timestamp
        fun rebound(model: IsRootDataModel, key: Bytes, expected: ULong?): ULong? =
            modified[model.Meta.name to key]?.let { (staged, durable) -> if (expected == staged) durable else expected } ?: expected
        val member = when (original) {
            is ChangeRequest<*> -> {
                @Suppress("UNCHECKED_CAST")
                val typed = original as ChangeRequest<IsRootDataModel>
                typed.dataModel.change(*typed.objects.map { obj ->
                    obj.key.change(*obj.changes.toTypedArray(), lastVersion =
                        rebound(typed.dataModel, Bytes(obj.key.bytes), obj.lastVersion))
                }.toTypedArray())
            }
            is DeleteRequest<*> -> {
                @Suppress("UNCHECKED_CAST")
                val typed = original as DeleteRequest<IsRootDataModel>
                typed.dataModel.delete(*typed.keys.toTypedArray(), hardDelete = typed.hardDelete, lastVersion =
                    typed.keys.firstOrNull()?.let { rebound(typed.dataModel, Bytes(it.bytes), typed.lastVersion) } ?: typed.lastVersion)
            }
            else -> original
        }
        @Suppress("UNCHECKED_CAST")
        val applied = apply(member as IsStoreRequest<IsRootDataModel, IsResponse>, operationVersion)
        val response = if (applied is ChangeResponse<*>) {
            @Suppress("UNCHECKED_CAST")
            val typed = applied as ChangeResponse<IsRootDataModel>
            typed.copy(statuses = typed.statuses.map {
            if (it is ChangeSuccess<*>) ChangeSuccess<IsRootDataModel>(it.version, it.changes?.takeIf { changes -> changes.isNotEmpty() }) else it
        }) } else applied
        val statuses = when (response) {
            is AddResponse<*> -> response.statuses
            is ChangeResponse<*> -> response.statuses
            is DeleteResponse<*> -> response.statuses
            else -> null
        }
        statuses?.filterIsInstance<ServerFail<*>>()?.firstOrNull()?.let { throw it.cause ?: TransactionAbort(
            TransactionResponse(TransactionStatus.OutcomeUnknown, message = it.reason)) }
        if (statuses?.any { it.statusType !in setOf(StatusType.ADD_SUCCESS, StatusType.CHANGE_SUCCESS, StatusType.DELETE_SUCCESS) } == true) {
            val failed = when (response) {
                is AddResponse<*> -> {
                    @Suppress("UNCHECKED_CAST") val typed = response as AddResponse<IsRootDataModel>
                    typed.copy(statuses = typed.statuses.filter { it.statusType != StatusType.ADD_SUCCESS })
                }
                is ChangeResponse<*> -> {
                    @Suppress("UNCHECKED_CAST") val typed = response as ChangeResponse<IsRootDataModel>
                    typed.copy(statuses = typed.statuses.filter { it.statusType != StatusType.CHANGE_SUCCESS })
                }
                is DeleteResponse<*> -> {
                    @Suppress("UNCHECKED_CAST") val typed = response as DeleteResponse<IsRootDataModel>
                    typed.copy(statuses = typed.statuses.filter { it.statusType != StatusType.DELETE_SUCCESS })
                }
                else -> response
            }
            throw TransactionAbort(TransactionResponse(TransactionStatus.Aborted, listOf(failed), failedRequestIndex = index.toUInt(),
                failure = AtomicFailure(AtomicFailureCode.ValidationFailed)))
        }
        if (response is ValuesResponse<*>) {
            rows += response.values.size
            if (rows > MAX_TRANSACTION_READ_ROWS.toLong()) rejectAtomicLimit("readRows", MAX_TRANSACTION_READ_ROWS.toULong(), rows.toULong())
        }
        when (response) {
            is AddResponse<*> -> response.statuses.filterIsInstance<AddSuccess<*>>().forEach {
                modified[member.dataModel.Meta.name to Bytes(it.key.bytes)] = provisionalVersion to it.version
            }
            is ChangeResponse<*> -> {
                val keys = (member as ChangeRequest<*>).objects.map { it.key }
                response.statuses.forEachIndexed { i, status -> if (status is ChangeSuccess<*> && status.version == operationVersion.timestamp) {
                    modified[member.dataModel.Meta.name to Bytes(keys[i].bytes)] = provisionalVersion to status.version
                } }
            }
            is DeleteResponse<*> -> {
                val keys = (member as DeleteRequest<*>).keys
                response.statuses.forEachIndexed { i, status -> if (status is DeleteSuccess<*>) {
                    modified[member.dataModel.Meta.name to Bytes(keys[i].bytes)] = provisionalVersion to status.version
                } }
            }
        }
        if (!request.commit || statuses != null) responses += response
    }
    return TransactionResponse(if (request.commit) TransactionStatus.Committed else TransactionStatus.Staged, responses,
        revision, version.timestamp + request.requests.size.toULong())
}

fun TransactionRequest.mutationKeys(): Map<IsRootDataModel, Set<Bytes>> = requests.groupBy { it.dataModel }.mapValues { (_, members) ->
    members.flatMap { member -> when (member) {
        is AddRequest<*> -> member.keysForObjects!!.map { Bytes(it.bytes) }
        is ChangeRequest<*> -> member.objects.filter { obj -> obj.changes.any { it !is Check } }.map { Bytes(it.key.bytes) }
        is DeleteRequest<*> -> member.keys.map { Bytes(it.bytes) }
        else -> emptyList()
    } }.toSet()
}

/** Generated values are echoed from their exact operation, without persisting request values in receipts. */
fun restoreTransactionGeneratedValues(response: TransactionResponse, request: TransactionRequest): TransactionResponse {
    if (response.status != TransactionStatus.Staged && response.status != TransactionStatus.Committed) return response
    val members = if (response.status == TransactionStatus.Committed) request.requests.filter {
        it is AddRequest<*> || it is ChangeRequest<*> || it is DeleteRequest<*>
    } else request.requests
    if (response.responses.size != members.size) return response
    return response.copy(responses = response.responses.mapIndexed { memberIndex, memberResponse ->
        val member = members[memberIndex] as? ChangeRequest<*> ?: return@mapIndexed memberResponse
        if (memberResponse !is ChangeResponse<*>) return@mapIndexed memberResponse
        @Suppress("UNCHECKED_CAST")
        val typed = memberResponse as ChangeResponse<IsRootDataModel>
        typed.copy(statuses = typed.statuses.mapIndexed statusMap@{ objectIndex, status ->
            if (status !is ChangeSuccess<*> || status.changes == null) return@statusMap status
            val inputs = member.objects[objectIndex].changes.filterIsInstance<IncMapChange>()
                .flatMap { it.valueChanges }.filter { !it.addValues.isNullOrEmpty() }.groupBy { it.reference.completeName }
            val occurrences = mutableMapOf<String, Int>()
            ChangeSuccess<IsRootDataModel>(status.version, status.changes!!.map { change ->
                if (change !is IncMapAddition) change else change.copy(additions = change.additions.map { addition ->
                    val name = addition.reference.completeName
                    val ordinal = occurrences[name] ?: 0
                    occurrences[name] = ordinal + 1
                    @Suppress("UNCHECKED_CAST")
                    val typedAddition = addition as IncMapKeyAdditions<Comparable<Any>, Any>
                    val values = inputs[name]?.getOrNull(ordinal)?.addValues
                    typedAddition.copy(addedValues = values?.toMutableList())
                })
            })
        })
    })
}

fun restoreAtomicGeneratedValues(response: AtomicResponse, request: AtomicRequest): AtomicResponse =
    restoreTransactionGeneratedValues(response.asTransactionResponse(), TransactionRequest(request.changeSetId,
        request.requests, initialGuards = request.guards, initialStateChecks = true)).asAtomicResponse()
