package maryk.datastore.shared

import kotlinx.atomicfu.atomic
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.requests.RequestType
import maryk.core.query.requests.MAX_ATOMIC_OPERATIONS
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.values.copyWithOwnedValues

/** Explicit capability for atomic replay; decorators can forward it independently of ordinary replication. */
interface VersionedRestoreDataStore {
    val restoreCapabilities: AtomicCapabilities?
    suspend fun restoreVersion(request: RestoreVersionRequest)
}

/** Backend-owned atomic replay; this is an internal routing request, not a transport API. */
class RestoreVersionRequest private constructor(
    updates: List<UpdateResponse<IsRootDataModel>>,
    private val stateBaseline: Boolean,
    staged: Boolean = false,
) : IsStoreRequest<IsRootDataModel, RestoreVersionResponse> {
    constructor(updates: List<UpdateResponse<IsRootDataModel>>) : this(updates, false)

    private val ownedUpdates = if (staged) copyStagedRestoreUpdates(updates) else snapshotRestoreUpdates(updates)
    private val payloadBytes = atomic(if (staged) UNKNOWN_PAYLOAD_BYTES else EXPOSED_PAYLOAD_BYTES)
    private var stagedByteUpperBound = Long.MAX_VALUE

    // A staged payload can retain its measurement until its mutable graph is exposed.
    val updates get() = ownedUpdates.also { payloadBytes.value = EXPOSED_PAYLOAD_BYTES }

    internal fun fitsSerializedUpdateLimit(limit: Long): Boolean =
        (payloadBytes.value != EXPOSED_PAYLOAD_BYTES && stagedByteUpperBound <= limit) || serializedUpdateSize() <= limit

    internal fun serializedUpdateSize(): Long {
        val cached = payloadBytes.value
        if (cached >= 0) return cached
        val codec = RestoreCodec(ownedUpdates.associate { it.dataModel.Meta.name to it.dataModel })
        val measured = ownedUpdates.sumOf {
            codec.replaySize(it.dataModel.Meta.name, it.update.version, (it.update as InitialChangesUpdate).changes).toLong()
        }
        payloadBytes.compareAndSet(UNKNOWN_PAYLOAD_BYTES, measured)
        return measured
    }

    /** Fresh authorization metadata; no mutable payload operands escape through this view. */
    fun requiredRequestTypes(): Map<IsRootDataModel, Set<RequestType>> = ownedUpdates.associate { response ->
        val types = mutableSetOf<RequestType>()
        for (record in (response.update as InitialChangesUpdate).changes) {
            for (version in record.changes) types += changeRequestTypes(version.changes)
        }
        response.dataModel to types
    }

    init {
        if (ownedUpdates.isEmpty()) throw RequestException("A restore version group cannot be empty")
        var sourceVersion: ULong? = null
        val modelNames = if (ownedUpdates.size > 1) mutableSetOf<String>() else null
        for (response in ownedUpdates) {
            val update = response.update as? InitialChangesUpdate
                ?: throw RequestException("Restore requires versioned record changes")
            requireSafeUpdateVersion(update.version)
            if (modelNames != null && !modelNames.add(response.dataModel.Meta.name)) throw RequestException("Duplicate restore model groups")
            val records = update.changes
            if (records.isEmpty()) throw RequestException("Restore requires one version per record in a group")
            val keys = if (records.size > 1) mutableSetOf<Bytes>() else null
            for (record in records) {
                if (record.changes.size != 1) throw RequestException("Restore requires one version per record in a group")
                val version = record.changes.single().version
                requireSafeUpdateVersion(version)
                if (version >= update.version) throw RequestException("Restore version must precede its snapshot boundary")
                if (!stateBaseline && sourceVersion != null && sourceVersion != version) {
                    throw RequestException("Restore group must contain one source version")
                }
                sourceVersion = version
                if (keys != null && !keys.add(record.key)) throw RequestException("Duplicate restore record identities")
            }
        }
    }
    /** Creation metadata to install atomically for a current-state baseline; absent for history replay. */
    var stateCreationVersions: Map<Key<IsRootDataModel>, ULong> = emptyMap()
        private set

    var stateAllocations: Map<Key<IsRootDataModel>, StateBackupAllocations> = emptyMap()
        private set

    companion object {
        private const val UNKNOWN_PAYLOAD_BYTES = -1L
        private const val EXPOSED_PAYLOAD_BYTES = -2L

        internal fun staged(updates: List<UpdateResponse<IsRootDataModel>>, serializedBytes: Long) =
            staged(updates).also { it.payloadBytes.value = serializedBytes }

        internal fun stagedWithByteUpperBound(updates: List<UpdateResponse<IsRootDataModel>>, upperBound: Long) =
            staged(updates).also {
                require(upperBound >= 0)
                it.stagedByteUpperBound = upperBound
            }

        /** Only restore's privately decoded, admitted staging groups may use this path. */
        internal fun staged(updates: List<UpdateResponse<IsRootDataModel>>) = RestoreVersionRequest(updates, false, true)

        internal fun state(model: IsRootDataModel, records: List<ValuesWithMetaData<IsRootDataModel>>, allocations: Map<Key<IsRootDataModel>, StateBackupAllocations> = emptyMap()): RestoreVersionRequest {
            if (records.isEmpty()) throw RequestException("A state restore group cannot be empty")
            records.forEach(::validateStateRecord)
            val detachedAllocations = snapshotStateAllocationRecords(model, records.map { it.key }.toSet(), allocations)
            val version = records.maxOf { it.lastVersion }
            val changes = records.map { record ->
                DataObjectVersionedChange(record.key, changes = listOf(VersionedChanges(record.lastVersion, buildList {
                    add(ObjectCreate)
                    addAll(record.values.toChanges())
                    if (record.isDeleted) add(ObjectSoftDeleteChange(true))
                })))
            }
            return RestoreVersionRequest(listOf(UpdateResponse(model, InitialChangesUpdate(version + 1uL, changes))), true).also { request ->
                request.stateAllocations = detachedAllocations
                request.stateCreationVersions = records.associate { Key<IsRootDataModel>(it.key.bytes.copyOf()) to it.firstVersion }
            }
        }
    }

    override val dataModel = ownedUpdates.first().dataModel
    val version = ownedUpdates.maxOf { (it.update as InitialChangesUpdate).changes.maxOf { record -> record.changes.single().version } }
    internal val stateChangeVersions get() = ownedUpdates.flatMap { (it.update as InitialChangesUpdate).changes }
        .associate { it.key to it.changes.single().version }
    val targets = ownedUpdates.associate { update ->
        update.dataModel to (update.update as InitialChangesUpdate).changes.map { it.key }
    }
}

/** Staging has already performed codec validation; each request still owns its containers. */
private fun copyStagedRestoreUpdates(updates: List<UpdateResponse<IsRootDataModel>>): List<UpdateResponse<IsRootDataModel>> {
    if (updates.sumOf { (it.update as? InitialChangesUpdate)?.changes?.size?.toLong() ?: 0L } > MAX_ATOMIC_OPERATIONS.toLong()) {
        throw RequestException("Restore version group exceeds mutation limit")
    }
    return updates.map { response ->
        val update = response.update as? InitialChangesUpdate ?: throw RequestException("Restore requires versioned record changes")
        UpdateResponse(response.dataModel, InitialChangesUpdate(update.version, update.changes.map { record ->
            record.copy(changes = record.changes.map { version ->
                version.copy(changes = version.changes.map { it.copyWithOwnedValues() })
            })
        }))
    }
}

private fun snapshotRestoreUpdates(updates: List<UpdateResponse<IsRootDataModel>>): List<UpdateResponse<IsRootDataModel>> {
    if (updates.sumOf { (it.update as? InitialChangesUpdate)?.changes?.size?.toLong() ?: 0L } > MAX_ATOMIC_OPERATIONS.toLong()) {
        throw RequestException("Restore version group exceeds mutation limit")
    }
    val codec = RestoreCodec(updates.associate { it.dataModel.Meta.name to it.dataModel })
    var remaining = MAX_ATOMIC_BYTES
    return updates.map { response ->
        val update = response.update as? InitialChangesUpdate ?: throw RequestException("Restore requires versioned record changes")
        val records = update.changes.map { record ->
            val encoded = codec.encode(response.dataModel.Meta.name, record, remaining)
            remaining -= encoded.size
            codec.decode(response.dataModel.Meta.name, encoded)
        }
        UpdateResponse(response.dataModel, InitialChangesUpdate(update.version, records))
    }
}

object RestoreVersionResponse : IsResponse

data class RestoreUniqueClaim(
    val model: IsRootDataModel,
    val key: Key<IsRootDataModel>,
    val qualifier: ByteArray,
    val value: Comparable<Any>,
    val encodedValue: ByteArray,
)

/** Called only inside a backend's transaction/copy and its buffered update context. */
suspend fun applyRestoreVersion(
    request: RestoreVersionRequest,
    read: suspend (IsRootDataModel, Key<IsRootDataModel>) -> ValuesWithMetaData<IsRootDataModel>?,
    clearUnique: suspend (List<RestoreUniqueClaim>) -> Unit,
    apply: suspend (UpdateResponse<IsRootDataModel>) -> ProcessResponse<IsRootDataModel>,
    installUnique: suspend (List<RestoreUniqueClaim>) -> Unit,
) {
    val uniqueModels = request.targets.keys.filterTo(mutableSetOf()) { it.mayContainUniqueValues() }
    val active = mutableMapOf<IsRootDataModel, List<Key<IsRootDataModel>>>()
    val before = mutableListOf<ValuesWithMetaData<IsRootDataModel>>()
    for ((model, keys) in request.targets) {
        active[model] = keys.filter { key ->
            val record = read(model, key)
            if (request.stateCreationVersions.isNotEmpty() && record != null) throw RequestException("State import cannot overwrite an existing record")
            (record == null || record.lastVersion < request.version).also { include ->
                if (include && record != null && model in uniqueModels) before += record
            }
        }
    }
    fun claims(records: List<ValuesWithMetaData<IsRootDataModel>>) = buildList {
        for (record in records) {
            if (record.isDeleted || record.values.dataModel !in uniqueModels) continue
            record.values.writeToStorage { type, qualifier, definition, value ->
                if (type == Value && definition is IsComparableDefinition<*, *> && definition.unique) {
                    @Suppress("UNCHECKED_CAST")
                    add(RestoreUniqueClaim(record.values.dataModel, record.key, qualifier, value as Comparable<Any>,
                        Value.castDefinition(definition).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)))
                }
            }
        }
    }
    if (uniqueModels.isNotEmpty()) clearUnique(claims(before))
    for (update in request.updates) {
        val changes = (update.update as InitialChangesUpdate).changes.filter { it.key in active.getValue(update.dataModel) }
        if (changes.isEmpty()) continue
        val filtered = UpdateResponse(update.dataModel, InitialChangesUpdate(update.update.version, changes))
        val response = apply(filtered).result as? AddOrChangeResponse<*>
            ?: throw RequestException("Unexpected version-group restore response")
        val expected = changes.size
        if (response.statuses.size != expected || response.statuses.any { it !is AddSuccess<*> && it !is ChangeSuccess<*> }) {
            throw RequestException("Could not restore version ${request.version}: ${response.statuses.joinToString()}")
        }
    }
    if (uniqueModels.isEmpty() && request.stateCreationVersions.isEmpty()) return

    val after = mutableListOf<ValuesWithMetaData<IsRootDataModel>>()
    for ((model, keys) in active) {
        if (model !in uniqueModels && request.stateCreationVersions.isEmpty()) continue
        for (key in keys) read(model, key)?.let { after += it }
    }
    if (request.stateCreationVersions.isNotEmpty()) {
        val expectedStateVersions = request.stateChangeVersions
        if (after.size != request.stateCreationVersions.size || after.any { it.lastVersion != expectedStateVersions[it.key] }) {
            throw RequestException("State import did not install every record at its source version")
        }
    }
    if (uniqueModels.isEmpty()) return

    val finalClaims = claims(after)
    val owners = mutableMapOf<Triple<String, Bytes, Bytes>, Key<IsRootDataModel>>()
    for (claim in finalClaims) {
        val identity = Triple(claim.model.Meta.name, Bytes(claim.qualifier), Bytes(claim.encodedValue))
        val previous = owners.put(identity, claim.key)
        if (previous != null && previous != claim.key) {
            throw RequestException("Restore version ${request.version} contains duplicate final unique values")
        }
    }
    clearUnique(finalClaims)
    installUnique(finalClaims)
}

fun validateRestoreVersion(request: RestoreVersionRequest, capabilities: AtomicCapabilities) {
    val count = request.targets.values.sumOf { it.size.toLong() }
    if (count > capabilities.maxMutations.toLong()) throw RequestException("Restore version group exceeds mutation limit")
    val allocationBytes = request.stateCreationVersions.keys.sumOf { key ->
        8L + key.size + stateAllocationBytes(request.stateAllocations[key].orEmpty())
    }
    val remainingBytes = capabilities.maxRequestBytes.toLong() - allocationBytes
    if (remainingBytes < 0 || !request.fitsSerializedUpdateLimit(remainingBytes)) throw RequestException("Restore version group exceeds byte limit")
}
