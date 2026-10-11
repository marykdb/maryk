package maryk.core.query.requests

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.changes.DataObjectChange
import maryk.core.query.changes.change
import maryk.core.values.Values
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.Check
import maryk.core.query.responses.AtomicResponse
import maryk.core.values.SimpleObjectValues

const val MAX_ATOMIC_OPERATIONS = 1_000u

class AtomicRequestLimitException(val limit: String, val maximum: UInt, val actual: UInt) :
    RequestException("Atomic $limit exceeds limit: $actual > $maximum")

/**
 * A bounded, all-or-nothing mutation across models in one datastore.
 * Each member addresses one record. Keys for additions are allocated once when constructing
 * the request, so references and retries use the same identity. Guards are check-only changes
 * evaluated before any mutations. Hard deletion, creation through Change, and nested requests
 * are deliberately excluded from this contract.
 *
 * Reuse [changeSetId] only with the identical request. Successful receipts are retained until
 * the datastore is destroyed (in Memory, until it is closed).
 */
class AtomicRequest(
    val changeSetId: String,
    requests: List<IsStoreRequest<*, *>>,
    guards: List<ChangeRequest<*>> = emptyList(),
) : IsTransportableRequest<AtomicResponse> {
    val requests: List<IsStoreRequest<*, *>>
    val guards = guards.toList()
    override val requestType = RequestType.Atomic
    override val responseModel = AtomicResponse

    constructor(changeSetId: String, vararg requests: IsStoreRequest<*, *>) :
        this(changeSetId, requests.toList())

    init {
        if (changeSetId.isBlank() || changeSetId.length > 128) {
            throw RequestException("Atomic changeSetId must contain 1 to 128 characters")
        }
        if (requests.isEmpty()) throw RequestException("Atomic requests require at least one mutation")
        if (requests.size.toUInt() > MAX_ATOMIC_OPERATIONS) throw AtomicRequestLimitException("mutations", MAX_ATOMIC_OPERATIONS, requests.size.toUInt())
        if (guards.size.toUInt() > MAX_ATOMIC_OPERATIONS) throw AtomicRequestLimitException("guards", MAX_ATOMIC_OPERATIONS, guards.size.toUInt())
        this.guards.forEach {
            if (it.objects.size != 1 || it.objects.single().changes.any { change -> change !is Check }) {
                throw RequestException("Atomic guards must be single-record, check-only Change requests")
            }
        }
        val targets = mutableSetOf<Pair<String, String>>()
        this.requests = requests.map { request ->
            @Suppress("UNCHECKED_CAST")
            val normalized: IsStoreRequest<*, *> = when (request) {
                is AddRequest<*> -> {
                    if (request.objects.size != 1) throw RequestException("Atomic Add requires exactly one object")
                    val add = request as AddRequest<IsRootDataModel>
                    AddRequest(add.dataModel, add.objects.toList(), add.keysForObjects ?: listOf(add.dataModel.key(add.objects.single())))
                }
                is ChangeRequest<*> -> {
                    if (request.objects.size != 1 || request.objects.single().changes.any { it is ObjectCreate }) throw RequestException("Atomic Change requires one existing object")
                    request.requireSupportedMutationChanges()
                    request
                }
                is DeleteRequest<*> -> {
                    if (request.keys.size != 1 || request.hardDelete) throw RequestException("Atomic Delete requires one key and soft deletion")
                    request
                }
                else -> throw RequestException("Atomic requests accept only Add, Change and soft Delete")
            }
            val key = when (normalized) {
                is AddRequest<*> -> normalized.keysForObjects!!.single()
                is ChangeRequest<*> -> normalized.objects.single().key
                is DeleteRequest<*> -> normalized.keys.single()
                else -> error("Unreachable")
            }
            if (!targets.add(normalized.dataModel.Meta.name to key.toString())) {
                throw RequestException("Atomic requests cannot mutate the same model/key twice")
            }
            normalized
        }
    }

    companion object : SimpleQueryModel<AtomicRequest>() {
        val changeSetId by string(1u, AtomicRequest::changeSetId, maxSize = 128u)
        val requests by list(
            index = 2u,
            getter = { value -> value.requests.map { TypedValue((it as IsTransportableRequest<*>).requestType, it) } },
            maxSize = MAX_ATOMIC_OPERATIONS,
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = RequestType,
                definitionMap = mapOf(
                    RequestType.Add to EmbeddedObjectDefinition(dataModel = { AddRequest }),
                    RequestType.Change to EmbeddedObjectDefinition(dataModel = { ChangeRequest }),
                    RequestType.Delete to EmbeddedObjectDefinition(dataModel = { DeleteRequest }),
                ),
            ),
        )
        val guards by list(
            index = 3u,
            getter = AtomicRequest::guards,
            maxSize = MAX_ATOMIC_OPERATIONS,
            valueDefinition = EmbeddedObjectDefinition(dataModel = { ChangeRequest }),
            default = emptyList(),
        )

        override fun invoke(values: SimpleObjectValues<AtomicRequest>) = AtomicRequest(
            changeSetId = values(changeSetId.index),
            requests = values<List<TypedValue<RequestType, Any>>>(requests.index).map { it.value as IsStoreRequest<*, *> },
            guards = values(guards.index),
        )
    }
}

/** Builds a serializable change-set; this block performs no datastore I/O. */
fun atomicRequest(changeSetId: String, block: AtomicRequestBuilder.() -> Unit): AtomicRequest =
    AtomicRequestBuilder(changeSetId).apply(block).build()

class AtomicRequestBuilder internal constructor(private val changeSetId: String) {
    private val mutations = mutableListOf<IsStoreRequest<*, *>>()
    private val guards = mutableListOf<ChangeRequest<*>>()

    fun <DM : IsRootDataModel> add(model: DM, values: Values<DM>): Key<DM> {
        val key = model.key(values)
        mutations += AddRequest(model, listOf(values), listOf(key))
        return key
    }

    fun <DM : IsRootDataModel> change(model: DM, change: DataObjectChange<DM>) {
        mutations += model.change(change)
    }

    fun <DM : IsRootDataModel> delete(model: DM, key: Key<DM>, lastVersion: ULong? = null) {
        mutations += model.delete(key, lastVersion = lastVersion)
    }

    fun <DM : IsRootDataModel> expectVersion(model: DM, key: Key<DM>, version: ULong) {
        guards += model.change(key.change(lastVersion = version))
    }

    fun <DM : IsRootDataModel> expect(model: DM, key: Key<DM>, vararg checks: Check) {
        guards += model.change(key.change(*checks))
    }

    internal fun build() = AtomicRequest(changeSetId, mutations, guards)
}
