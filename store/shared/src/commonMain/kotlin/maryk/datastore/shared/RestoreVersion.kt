package maryk.datastore.shared

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
import maryk.core.query.requests.MAX_ATOMIC_OPERATIONS
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.ProcessResponse

/** Explicit capability for atomic replay; decorators can forward it independently of ordinary replication. */
interface VersionedRestoreDataStore {
    val restoreCapabilities: AtomicCapabilities?
    suspend fun restoreVersion(request: RestoreVersionRequest)
}

/** Backend-owned atomic replay; this is an internal routing request, not a transport API. */
class RestoreVersionRequest private constructor(
    updates: List<UpdateResponse<IsRootDataModel>>,
    private val stateBaseline: Boolean,
) : IsStoreRequest<IsRootDataModel, RestoreVersionResponse> {
    constructor(updates: List<UpdateResponse<IsRootDataModel>>) : this(updates, false)

    val updates = snapshotRestoreUpdates(updates)

    init {
        if (this.updates.isEmpty()) throw RequestException("A restore version group cannot be empty")
        val versions = this.updates.flatMap { response ->
            requireSafeUpdateVersion(response.update)
            val update = response.update as? InitialChangesUpdate
                ?: throw RequestException("Restore requires versioned record changes")
            if (update.changes.isEmpty() || update.changes.any { it.changes.size != 1 }) {
                throw RequestException("Restore requires one version per record in a group")
            }
            if (update.changes.any { it.changes.single().version >= update.version }) throw RequestException("Restore version must precede its snapshot boundary")
            update.changes.map { it.changes.single().version }
        }
        if (!stateBaseline && versions.distinct().size != 1) throw RequestException("Restore group must contain one source version")
        if (this.updates.map { it.dataModel.Meta.name }.distinct().size != this.updates.size) throw RequestException("Duplicate restore model groups")
        for (update in this.updates) {
            val records = (update.update as InitialChangesUpdate).changes
            if (records.map { Bytes(it.key.bytes) }.distinct().size != records.size) throw RequestException("Duplicate restore record identities")
        }
    }
    /** Creation metadata to install atomically for a current-state baseline; absent for history replay. */
    var stateCreationVersions: Map<Key<IsRootDataModel>, ULong> = emptyMap()
        private set

    var stateAllocations: Map<Key<IsRootDataModel>, StateBackupAllocations> = emptyMap()
        private set

    companion object {
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

    override val dataModel = this.updates.first().dataModel
    val version = this.updates.maxOf { (it.update as InitialChangesUpdate).changes.maxOf { record -> record.changes.single().version } }
    internal val stateChangeVersions get() = updates.flatMap { (it.update as InitialChangesUpdate).changes }
        .associate { it.key to it.changes.single().version }
    val targets = this.updates.associate { update ->
        update.dataModel to (update.update as InitialChangesUpdate).changes.map { it.key }
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
    val active = mutableMapOf<IsRootDataModel, List<Key<IsRootDataModel>>>()
    val before = mutableListOf<ValuesWithMetaData<IsRootDataModel>>()
    for ((model, keys) in request.targets) {
        active[model] = keys.filter { key ->
            val record = read(model, key)
            if (request.stateCreationVersions.isNotEmpty() && record != null) throw RequestException("State import cannot overwrite an existing record")
            (record == null || record.lastVersion < request.version).also { include ->
                if (include && record != null) before += record
            }
        }
    }
    fun claims(records: List<ValuesWithMetaData<IsRootDataModel>>) = buildList {
        for (record in records) {
            if (record.isDeleted) continue
            record.values.writeToStorage { type, qualifier, definition, value ->
                if (type == Value && definition is IsComparableDefinition<*, *> && definition.unique) {
                    @Suppress("UNCHECKED_CAST")
                    add(RestoreUniqueClaim(record.values.dataModel, record.key, qualifier, value as Comparable<Any>,
                        Value.castDefinition(definition).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)))
                }
            }
        }
    }
    clearUnique(claims(before))
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
    val after = mutableListOf<ValuesWithMetaData<IsRootDataModel>>()
    for ((model, keys) in active) for (key in keys) read(model, key)?.let { after += it }
    val expectedStateVersions = request.stateChangeVersions
    if (request.stateCreationVersions.isNotEmpty() && (after.size != request.stateCreationVersions.size || after.any { it.lastVersion != expectedStateVersions[it.key] })) {
        throw RequestException("State import did not install every record at its source version")
    }
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
    val codec = RestoreCodec(request.updates.associate { it.dataModel.Meta.name to it.dataModel })
    val bytes = request.updates.sumOf {
        codec.replaySize(it.dataModel.Meta.name, it.update.version, (it.update as InitialChangesUpdate).changes).toLong()
    }
    val allocationBytes = request.stateCreationVersions.keys.sumOf { key ->
        8L + key.size + stateAllocationBytes(request.stateAllocations[key].orEmpty())
    }
    if (bytes + allocationBytes > capabilities.maxRequestBytes.toLong()) throw RequestException("Restore version group exceeds byte limit")
}
