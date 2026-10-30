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
import maryk.core.query.changes.IncMapAddition
import maryk.core.models.migration.ReleasedTransportFingerprintException
import maryk.core.models.migration.readLegacyTransportStorage
import maryk.core.models.migration.writeReleasedTransportForFingerprint
import maryk.core.models.migration.toReleasedReferenceStorageByteArray
import maryk.core.models.migration.requiresKeyStorageFormatUpgrade
import maryk.core.models.migration.convertLegacyIndexBytes
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.changes.IndexDelete
import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.AtomicFailure
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.AtomicResponse
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
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

/** Atomic execution and its resource ceilings; unsupported stores report null capabilities. */
interface AtomicDataStore {
    val supportsAtomicRequests: Boolean
    val atomicCapabilities: AtomicCapabilities?
    suspend fun executeAtomic(request: AtomicRequest): AtomicResponse
}

/** Atomic implementations must report their limits when advertising support. */
val IsDataStore.atomicCapabilities: AtomicCapabilities?
    get() = if (!supportsAtomicRequests) null else
        requireNotNull((this as AtomicDataStore).atomicCapabilities) { "Atomic support requires capability metadata" }

val IsDataStore.supportsAtomicRequests: Boolean
    get() = (this as? AtomicDataStore)?.supportsAtomicRequests == true

suspend fun IsDataStore.executeAtomic(request: AtomicRequest): AtomicResponse {
    val store = (this as? AtomicDataStore)?.takeIf { it.supportsAtomicRequests }
        ?: return AtomicResponse(AtomicStatus.Unsupported, message = "This datastore does not support atomic requests")
    return try {
        validateAtomicCounts(request, requireNotNull(atomicCapabilities))
        store.executeAtomic(request)
    } catch (abort: AtomicAbort) { abort.response }
}

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

/** Limits reject the complete change set before publication. */
fun rejectAtomicLimit(limit: String, maximum: ULong, actual: ULong): Nothing = throw AtomicAbort(
    AtomicResponse(AtomicStatus.Rejected, message = "Atomic $limit exceeds limit: $actual > $maximum",
        failure = AtomicFailure(AtomicFailureCode.LimitExceeded, limit, maximum, actual)),
)

fun validateAtomicCounts(request: AtomicRequest, capabilities: AtomicCapabilities) {
    if (request.requests.size.toUInt() > capabilities.maxMutations) {
        rejectAtomicLimit("mutations", capabilities.maxMutations.toULong(), request.requests.size.toULong())
    }
    if (request.guards.size.toUInt() > capabilities.maxGuards) {
        rejectAtomicLimit("guards", capabilities.maxGuards.toULong(), request.guards.size.toULong())
    }
}

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

    private fun <T : Any> encode(model: IsTypedObjectDataModel<T, *, RequestContext, RequestContext>, value: T, limit: String, released: Boolean = false): ByteArray {
        val context = context()
        fun write(): ByteArray {
            val cache = WriteCache()
            val length = model.Serializer.calculateObjectProtoBufLength(value, cache, context)
            if (length > MAX_ATOMIC_BYTES) rejectAtomicLimit(limit, MAX_ATOMIC_BYTES.toULong(), length.toULong())
            val result = ByteArray(length)
            var offset = 0
            model.Serializer.writeObjectProtoBuf(value, cache, { result[offset++] = it }, context)
            return result
        }
        return if (released) context.writeReleasedTransportForFingerprint(::write) else write()
    }

    suspend fun transactionFingerprint(request: TransactionRequest): ByteArray =
        CryptographyProvider.Default.get(SHA256).hasher().hash(encode(TransactionRequest, request, "requestBytes"))

    suspend fun transactionFingerprints(request: TransactionRequest): TransactionFingerprints {
        val currentBytes = encode(TransactionRequest, request, "requestBytes")
        val hasher = CryptographyProvider.Default.get(SHA256).hasher()
        val current = hasher.hash(currentBytes)
        val releasedBytes = try {
            encode(TransactionRequest, request, "requestBytes", released = true)
        } catch (unsupported: ReleasedTransportFingerprintException) {
            return TransactionFingerprints(current, null, unsupported.message)
        }
        return TransactionFingerprints(current,
            if (currentBytes.contentEquals(releasedBytes)) current else hasher.hash(releasedBytes))
    }

    /** Own the submitted values, keys and predicates independently of caller-owned collections. */
    internal fun snapshotTransactionRequest(request: TransactionRequest): TransactionRequest {
        val bytes = encode(TransactionRequest, request, "requestBytes")
        var offset = 0
        return TransactionRequest.Serializer.readProtoBuf(bytes.size, { bytes[offset++] }, context()).toDataObject()
    }

    fun transactionReceipt(fingerprint: ByteArray, response: TransactionResponse): ByteArray =
        tagCurrentTransactionReceipt(fingerprint + encode(TransactionResponse, withoutGeneratedRequestValues(response), "responseBytes"))

    private fun withoutGeneratedRequestValues(response: TransactionResponse) = response.copy(
        responses = response.responses.map { member ->
            if (member !is ChangeResponse<*>) member else {
                @Suppress("UNCHECKED_CAST")
                val typed = member as ChangeResponse<IsRootDataModel>
                typed.copy(statuses = typed.statuses.map { status ->
                    if (status !is ChangeSuccess<*>) status else ChangeSuccess<IsRootDataModel>(
                        status.version,
                        status.changes?.map { change ->
                            if (change !is IncMapAddition) change else change.copy(
                                additions = change.additions.map { it.copy(addedValues = null) },
                            )
                        },
                    )
                })
            }
        },
    )

    fun replayTransaction(receipt: ByteArray, fingerprint: ByteArray, request: TransactionRequest): TransactionResponse {
        return replayTransaction(receipt, TransactionFingerprints(fingerprint, null,
            "Released request fingerprint was not supplied"), request)
    }

    fun replayTransaction(receipt: ByteArray, fingerprints: TransactionFingerprints, request: TransactionRequest): TransactionResponse {
        fun matches(offset: Int, fingerprint: ByteArray?) = fingerprint != null && receipt.size - offset >= fingerprint.size &&
            fingerprint.indices.all { receipt[offset + it] == fingerprint[it] }
        // Retain the original public raw-receipt reader, including a digest that
        // happens to start with the envelope magic. Full digest equality is decisive.
        val rawCurrent = matches(0, fingerprints.current)
        val version = if (rawCurrent) null else transactionReceiptVersion(receipt)
        if (version != null && version !in ReleasedReceiptVersion..CurrentReceiptVersion) {
            throw RequestException("Unsupported transaction receipt format $version")
        }
        val start = if (version == null) 0 else TransactionReceiptHeaderSize
        if (version == ReleasedReceiptVersion && fingerprints.released == null) {
            return TransactionResponse(TransactionStatus.Aborted,
                message = "Cannot prove this legacy transaction identity: ${fingerprints.releasedUnavailableReason ?: "Released request encoding unavailable"}. " +
                    "Resolve it with the original source-format environment; do not retry with a new transaction ID",
                failure = AtomicFailure(AtomicFailureCode.InvalidState))
        }
        val expected = if (version == ReleasedReceiptVersion) fingerprints.released else fingerprints.current
        if (!matches(start, expected)) {
            return TransactionResponse(TransactionStatus.Aborted, message = "Transaction ID was committed with different contents",
                failure = AtomicFailure(AtomicFailureCode.ChangeSetMismatch))
        }
        var offset = start + fingerprints.current.size
        val context = context()
        fun read() = TransactionResponse.Serializer.readProtoBuf(receipt.size - offset, { receipt[offset++] }, context).toDataObject()
        val response = if (version == ReleasedReceiptVersion) convertReleasedIndexMetadata(context.readLegacyTransportStorage(::read)) else read()
        return restoreTransactionGeneratedValues(response, request)
    }

    private fun convertReleasedIndexMetadata(response: TransactionResponse) = response.copy(
        responses = response.responses.map { member ->
            if (member !is ChangeResponse<*>) member else {
                @Suppress("UNCHECKED_CAST")
                val typed = member as ChangeResponse<IsRootDataModel>
                typed.copy(statuses = typed.statuses.map { status ->
                    if (status !is ChangeSuccess<*>) status else ChangeSuccess<IsRootDataModel>(status.version,
                        status.changes?.map { change ->
                            if (change !is IndexChange) change else IndexChange(change.changes.map { update ->
                                val index = typed.dataModel.Meta.indexes.orEmpty().firstOrNull {
                                    it.toReleasedReferenceStorageByteArray().contentEquals(update.index.bytes)
                                }
                                if (index == null) update else {
                                    fun convert(key: Bytes) = if (index.requiresKeyStorageFormatUpgrade()) {
                                        Bytes(index.convertLegacyIndexBytes(key.bytes, typed.dataModel.Meta.keyByteSize))
                                    } else key
                                    when (update) {
                                        is IndexUpdate -> IndexUpdate(index.referenceStorageByteArray, convert(update.indexKey), update.previousIndexKey?.let(::convert))
                                        is IndexDelete -> IndexDelete(index.referenceStorageByteArray, convert(update.indexKey))
                                        else -> update
                                    }
                                }
                            })
                        })
                })
            }
        },
    )


}

const val MAX_ATOMIC_BYTES = 1_048_576

const val MAX_ATOMIC_WORK_BYTES = 4_194_304
const val MAX_ATOMIC_STAGED_NODES = 100_000
const val MAX_ATOMIC_SCAN_ROWS = 100_000
