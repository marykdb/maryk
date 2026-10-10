package maryk.datastore.shared

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.currentCoroutineContext
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.Check
import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.AtomicResponse
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.IsDataModelResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.StatusType
import maryk.datastore.shared.updates.Update
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Optional capability; existing datastore implementations need not change. */
interface AtomicDataStore {
    val supportsAtomicRequests: Boolean
    suspend fun executeAtomic(request: AtomicRequest): AtomicResponse
}

val IsDataStore.supportsAtomicRequests: Boolean
    get() = (this as? AtomicDataStore)?.supportsAtomicRequests == true

suspend fun IsDataStore.executeAtomic(request: AtomicRequest): AtomicResponse =
    (this as? AtomicDataStore)?.takeIf { it.supportsAtomicRequests }?.executeAtomic(request)
        ?: AtomicResponse(AtomicStatus.Unsupported, message = "This datastore does not support atomic requests")

/** Internal routing adapter. The anchor is not the atomic request's public model or its validation scope. */
internal class AtomicStoreRequest(val atomic: AtomicRequest) : IsStoreRequest<IsRootDataModel, AtomicResponse> {
    override val dataModel = atomic.requests.first().dataModel
}

/** A backend-owned transaction and attempt-local effects, never shared with read worker coroutines. */
class AtomicMutationContext(val owner: Any, val transaction: Any) : AbstractCoroutineContextElement(Key) {
    val updates = mutableListOf<Update<*>>()
    companion object Key : CoroutineContext.Key<AtomicMutationContext>
}

suspend fun currentAtomicContext(owner: Any): AtomicMutationContext? =
    currentCoroutineContext()[AtomicMutationContext]?.takeIf { it.owner === owner }

class AtomicAbort(val response: AtomicResponse) : RuntimeException(response.message)

/** A deterministic pre-commit rejection, including storage-work limits. */
fun rejectAtomic(message: String): Nothing = throw AtomicAbort(AtomicResponse(AtomicStatus.Rejected, message = message))

/** Retains structured validation failures, but never returns staged success statuses after rollback. */
suspend fun applyAtomicMembers(
    request: AtomicRequest,
    apply: suspend (IsStoreRequest<IsRootDataModel, IsResponse>) -> IsDataModelResponse<*>,
): AtomicResponse {
    suspend fun applyOne(member: IsStoreRequest<*, *>, index: Int, guard: Boolean): IsDataModelResponse<*> {
        @Suppress("UNCHECKED_CAST")
        val original = apply(member as IsStoreRequest<IsRootDataModel, IsResponse>)
        val response = if (original is ChangeResponse<*>) {
            @Suppress("UNCHECKED_CAST")
            val typed = original as ChangeResponse<IsRootDataModel>
            typed.copy(statuses = typed.statuses.map {
                if (it is ChangeSuccess<*>) ChangeSuccess<IsRootDataModel>(it.version, it.changes?.takeIf { changes -> changes.isNotEmpty() }) else it
            })
        } else original
        val statuses = when (response) {
            is AddResponse<*> -> response.statuses
            is ChangeResponse<*> -> response.statuses
            is DeleteResponse<*> -> response.statuses
            else -> throw RequestException("Unsupported atomic response")
        }
        statuses.filterIsInstance<ServerFail<*>>().firstOrNull()?.let {
            throw it.cause ?: RequestException(it.reason)
        }
        if (statuses.size != 1 || statuses.any {
                it.statusType !in setOf(StatusType.ADD_SUCCESS, StatusType.CHANGE_SUCCESS, StatusType.DELETE_SUCCESS)
            }) {
            throw AtomicAbort(AtomicResponse(AtomicStatus.Rejected, listOf(response), index.toUInt(), guard))
        }
        return response
    }
    request.guards.forEachIndexed { index, guard -> applyOne(guard, index, true) }
    // Embedded Check and lastVersion are also evaluated before any member changes state.
    request.requests.forEachIndexed { index, member ->
        if (member is ChangeRequest<*>) {
            val obj = member.objects.single()
            if (obj.lastVersion != null || obj.changes.any { it is Check }) {
                @Suppress("UNCHECKED_CAST")
                val condition = member.dataModel.change(obj.key.change(
                    *obj.changes.filterIsInstance<Check>().toTypedArray(), lastVersion = obj.lastVersion,
                ))
                applyOne(condition, index, false)
            }
        } else if (member is DeleteRequest<*> && member.lastVersion != null) {
            val condition = member.dataModel.change(member.keys.single().change(lastVersion = member.lastVersion))
            applyOne(condition, index, false)
        }
    }
    val responses = request.requests.mapIndexed { index, member ->
        val mutation = if (member is ChangeRequest<*>) {
            val obj = member.objects.single()
            member.dataModel.change(obj.key.change(*obj.changes.filterNot { it is Check }.toTypedArray()))
        } else member
        applyOne(mutation, index, false)
    }
    return AtomicResponse(AtomicStatus.Committed, responses)
}

/** Receipts contain a request digest, never a copy of sensitive request values. */
class AtomicReceiptCodec(private val models: Map<UInt, IsRootDataModel>) {
    private fun context() = RequestContext(DefinitionsContext(
        dataModels = models.values.associate { it.Meta.name to DataModelReference(it) }.toMutableMap(),
    ))

    private fun <T : Any> encode(model: IsTypedObjectDataModel<T, *, RequestContext, RequestContext>, value: T): ByteArray {
        val cache = WriteCache()
        val context = context()
        val length = model.Serializer.calculateObjectProtoBufLength(value, cache, context)
        if (length > MAX_ATOMIC_BYTES) rejectAtomic("Atomic payload exceeds $MAX_ATOMIC_BYTES bytes")
        val result = ByteArray(length)
        var offset = 0
        model.Serializer.writeObjectProtoBuf(value, cache, { result[offset++] = it }, context)
        return result
    }

    suspend fun fingerprint(request: AtomicRequest): ByteArray =
        CryptographyProvider.Default.get(SHA256).hasher().hash(encode(AtomicRequest, request))

    fun receipt(fingerprint: ByteArray, response: AtomicResponse): ByteArray = fingerprint + encode(AtomicResponse, response)

    fun replay(receipt: ByteArray, fingerprint: ByteArray): AtomicResponse {
        if (receipt.size < fingerprint.size || !receipt.copyOfRange(0, fingerprint.size).contentEquals(fingerprint)) {
            return AtomicResponse(AtomicStatus.Rejected, message = "changeSetId was already committed with different contents")
        }
        var offset = fingerprint.size
        return AtomicResponse.Serializer.readProtoBuf(receipt.size - offset, { receipt[offset++] }, context()).toDataObject()
    }
}

const val MAX_ATOMIC_BYTES = 1_048_576
