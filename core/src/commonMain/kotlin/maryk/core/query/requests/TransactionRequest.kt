package maryk.core.query.requests

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.models.key
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.flexBytes
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange
import maryk.core.query.responses.TransactionResponse
import maryk.core.values.SimpleObjectValues

const val MAX_TRANSACTION_OPERATIONS = 2_000u
const val MAX_TRANSACTION_READ_KEYS = 100_000u
const val MAX_TRANSACTION_READ_ROWS = 100_000u

/** An ordered transaction journal. Previewing it never publishes writes. */
class TransactionRequest(
    val transactionId: String,
    requests: List<IsStoreRequest<*, *>> = emptyList(),
    val expectedRevision: Bytes? = null,
    val stagingVersion: ULong? = null,
    val commit: Boolean = false,
    val initialGuards: List<ChangeRequest<*>> = emptyList(),
    val initialStateChecks: Boolean = false,
    val readOnly: Boolean = false,
) : IsTransportableRequest<TransactionResponse> {
    val requests: List<IsStoreRequest<*, *>>
    val hasMutations: Boolean
    val mutationCount: UInt
    val readKeyCount: UInt
    override val requestType = RequestType.Transaction
    override val responseModel = TransactionResponse

    init {
        if (transactionId.isBlank() || transactionId.length > 128) throw RequestException("Transaction ID must contain 1 to 128 characters")
        if (requests.size.toUInt() > MAX_TRANSACTION_OPERATIONS) throw AtomicRequestLimitException("operations", MAX_TRANSACTION_OPERATIONS, requests.size.toUInt())
        if (initialGuards.size.toUInt() > MAX_ATOMIC_OPERATIONS) throw AtomicRequestLimitException("guards", MAX_ATOMIC_OPERATIONS, initialGuards.size.toUInt())
        if (expectedRevision != null && expectedRevision.size > 32) throw RequestException("Invalid transaction revision")
        if (stagingVersion != null && stagingVersion > ULong.MAX_VALUE - requests.size.toULong()) throw RequestException("Transaction staging version overflows the ordered journal")
        if (initialGuards.isNotEmpty() && !initialStateChecks) throw RequestException("Initial guards require initial-state checks")
        val mutations = requests.sumOf { member -> when (member) {
            is AddRequest<*> -> member.objects.size
            is ChangeRequest<*> -> member.objects.count { obj -> obj.changes.any { it !is Check } }
            is DeleteRequest<*> -> member.keys.size
            else -> 0
        } }
        mutationCount = mutations.toUInt()
        hasMutations = mutations > 0
        if (mutations.toUInt() > MAX_ATOMIC_OPERATIONS) throw AtomicRequestLimitException("mutations", MAX_ATOMIC_OPERATIONS, mutations.toUInt())
        val readKeys = initialGuards.size + requests.sumOf { member -> when (member) {
            is GetRequest<*> -> member.keys.size
            is ChangeRequest<*> -> member.objects.count { obj -> obj.changes.all { it is Check } }
            else -> 0
        } }
        readKeyCount = readKeys.toUInt()
        if (readKeys.toUInt() > MAX_TRANSACTION_READ_KEYS) throw AtomicRequestLimitException("readKeys", MAX_TRANSACTION_READ_KEYS, readKeys.toUInt())
        this.requests = requests.map { request ->
            when (request) {
                is AddRequest<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val add = request as AddRequest<IsRootDataModel>
                    AddRequest(add.dataModel, add.objects.toList(), add.keysForObjects ?: add.objects.map { add.dataModel.key(it) })
                }
                is ChangeRequest<*> -> request.also { it.requireSupportedMutationChanges() }
                is DeleteRequest<*>, is GetRequest<*>, is ScanRequest<*> -> request
                else -> throw RequestException("Transactions support Add, Change, Delete, Get and Scan")
            }
        }
        this.requests.forEach { request ->
            val keys = when (request) {
                is AddRequest<*> -> request.keysForObjects.orEmpty()
                is ChangeRequest<*> -> request.objects.map { it.key }
                is DeleteRequest<*> -> request.keys
                else -> emptyList()
            }
            if (keys.toSet().size != keys.size) throw RequestException("Each mutation request must target a key only once; use separate requests for ordered repeated writes")
        }
        if (readOnly && hasMutations) throw RequestException("Read-only transactions cannot mutate records")
        if (initialGuards.any { it.objects.size != 1 || it.objects.single().changes.any { change -> change !is Check } }) {
            throw RequestException("Initial transaction guards must be single-record checks")
        }
        if (initialStateChecks) AtomicRequest(transactionId, this.requests, initialGuards)
    }

    companion object : SimpleQueryModel<TransactionRequest>() {
        val transactionId by string(1u, TransactionRequest::transactionId, maxSize = 128u)
        val requests by list(
            2u,
            getter = { value -> value.requests.map { TypedValue((it as IsTransportableRequest<*>).requestType, it) } },
            maxSize = MAX_TRANSACTION_OPERATIONS,
            default = emptyList(),
            valueDefinition = InternalMultiTypeDefinition(typeEnum = RequestType, definitionMap = mapOf(
                RequestType.Add to EmbeddedObjectDefinition(dataModel = { AddRequest }),
                RequestType.Change to EmbeddedObjectDefinition(dataModel = { ChangeRequest }),
                RequestType.Delete to EmbeddedObjectDefinition(dataModel = { DeleteRequest }),
                RequestType.Get to EmbeddedObjectDefinition(dataModel = { GetRequest }),
                RequestType.Scan to EmbeddedObjectDefinition(dataModel = { ScanRequest }),
            )),
        )
        val expectedRevision by flexBytes(3u, TransactionRequest::expectedRevision, required = false, maxSize = 32u)
        val stagingVersion by number(4u, TransactionRequest::stagingVersion, UInt64, required = false)
        val commit by boolean(5u, TransactionRequest::commit, default = false)
        val initialGuards by list(6u, TransactionRequest::initialGuards, default = emptyList(), maxSize = MAX_ATOMIC_OPERATIONS,
            valueDefinition = EmbeddedObjectDefinition(dataModel = { ChangeRequest }))
        val initialStateChecks by boolean(7u, TransactionRequest::initialStateChecks, default = false)
        val readOnly by boolean(8u, TransactionRequest::readOnly, default = false)
        override fun invoke(values: SimpleObjectValues<TransactionRequest>) = TransactionRequest(
            values(transactionId.index),
            values<List<TypedValue<RequestType, Any>>>(requests.index).map { it.value as IsStoreRequest<*, *> },
            values(expectedRevision.index), values(stagingVersion.index), values(commit.index),
            values(initialGuards.index), values(initialStateChecks.index), values(readOnly.index),
        )
    }
}

/** Creation and generated replication/index changes belong to the replication API. */
internal fun ChangeRequest<*>.requireSupportedMutationChanges() {
    if (objects.any { obj -> obj.changes.any {
        it !is Check && it !is Change && it !is ListChange && it !is SetChange && it !is IncMapChange && it !is ObjectSoftDeleteChange
    } }) throw RequestException("Unsupported mutation change; use Add for record creation and processUpdate for replication")
}
