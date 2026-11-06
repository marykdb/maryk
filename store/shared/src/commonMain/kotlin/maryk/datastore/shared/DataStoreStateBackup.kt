package maryk.datastore.shared

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.ScanCursor
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.scan
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.ValuesResponse
import maryk.lib.extensions.compare.compareTo

const val DATA_STORE_STATE_BACKUP_FORMAT_VERSION = 2u

/** A consistent logical image, without claiming that historical revisions are available. */
data class DataStoreStateBackupManifest(
    val modelNames: List<String>,
    val modelMajorVersions: Map<String, UShort>,
    val formatVersion: UInt = DATA_STORE_STATE_BACKUP_FORMAT_VERSION,
)

/**
 * Records and models occur in manifest order, with strictly increasing keys within each model.
 * Transports must retain [allocations], including references whose parents are currently absent.
 * A missing record/reference entry means that no lifetime maximum has been allocated for it.
 */
data class DataStoreStateBackupChunk(
    val modelName: String,
    val records: List<ValuesWithMetaData<IsRootDataModel>>,
    val allocations: Map<Key<IsRootDataModel>, StateBackupAllocations> = emptyMap(),
)

interface DataStoreStateBackupWriter {
    /** Keep the output private until complete returns successfully. */
    suspend fun begin(manifest: DataStoreStateBackupManifest)
    suspend fun write(chunk: DataStoreStateBackupChunk)
    suspend fun complete()
}

interface DataStoreStateBackupReader {
    val manifest: DataStoreStateBackupManifest
    /**
     * Read one complete, integrity-checked archive. Invoke and await the consumer sequentially,
     * propagate its failures, and throw on truncation or corruption before returning.
     */
    suspend fun read(consumer: suspend (DataStoreStateBackupChunk) -> Unit)
}

/** Bounds both records and serialized payload, including a single large record. */
data class DataStoreStateBackupOptions(
    val batchSize: UInt = 250u,
    val maxChunkBytes: Int = MAX_ATOMIC_BYTES,
) {
    init {
        require(batchSize in 1u..1000u) { "State backup batch size must be between 1 and 1000" }
        require(maxChunkBytes in 1..MAX_ATOMIC_BYTES) { "State backup chunk byte limit is out of range" }
    }
}

/** Explicit guarantee of one stable view across every model and page, and atomic state import. */
interface StateBackupDataStore {
    val supportsStateBackup: Boolean
    suspend fun writeStateBackup(writer: DataStoreStateBackupWriter, options: DataStoreStateBackupOptions): DataStoreStateBackupManifest
    suspend fun restoreStateVersion(model: IsRootDataModel, records: List<ValuesWithMetaData<IsRootDataModel>>, allocations: Map<Key<IsRootDataModel>, StateBackupAllocations>)
}

val IsDataStore.supportsStateBackup: Boolean
    get() = (this as? StateBackupDataStore)?.supportsStateBackup == true

suspend fun IsDataStore.backupState(
    writer: DataStoreStateBackupWriter,
    options: DataStoreStateBackupOptions = DataStoreStateBackupOptions(),
): DataStoreStateBackupManifest = stateBackupStore().writeStateBackup(writer, options)

private fun IsDataStore.stateBackupStore(): StateBackupDataStore =
    (this as? StateBackupDataStore)?.takeIf { it.supportsStateBackup }
        ?: throw RequestException("This datastore does not support consistent state backup and restore")

/** Routing only: never registered, scanned or included in a manifest. */
internal object EmptyStateBackupModel : RootDataModel<EmptyStateBackupModel>()

internal class StateBackupRequest(
    override val dataModel: IsRootDataModel,
    val writer: DataStoreStateBackupWriter,
    val options: DataStoreStateBackupOptions,
    val callerJob: Job?,
) : IsStoreRequest<IsRootDataModel, StateBackupResponse>

internal class StateBackupResponse(val manifest: DataStoreStateBackupManifest) : IsResponse

/** Runs only inside the backend's stable read scope. No calls are routed back through the actor. */
internal suspend fun IsDataStore.captureStateBackup(
    request: StateBackupRequest,
    scan: suspend (ScanRequest<IsRootDataModel>) -> ValuesResponse<IsRootDataModel>,
    readAllocations: suspend (IsRootDataModel, Key<IsRootDataModel>) -> StateBackupAllocations,
): StateBackupResponse {
    val models = dataModelsById.values.sortedBy { it.Meta.name }
    val manifest = DataStoreStateBackupManifest(models.map { it.Meta.name }, models.associate { it.Meta.name to it.Meta.version.major })
    val codec = StateBackupCodec(models.associateBy { it.Meta.name })
    request.writer.begin(manifest)
    for (model in models) {
        var cursor: ScanCursor? = null
        var previous: Key<IsRootDataModel>? = null
        do {
            currentCoroutineContext().ensureActive()
            val page = scan(model.scan(cursor = cursor, limit = request.options.batchSize, filterSoftDeleted = false, allowTableScan = true))
            val records = mutableListOf<ValuesWithMetaData<IsRootDataModel>>()
            val allocations = mutableMapOf<Key<IsRootDataModel>, StateBackupAllocations>()
            var bytes = 0
            suspend fun writeRecords(batch: List<ValuesWithMetaData<IsRootDataModel>>) {
                if (batch.isEmpty()) return
                val keys = batch.map { it.key }.toSet()
                val batchAllocations = allocations.filterKeys { it in keys }
                try {
                    // State values can encode more compactly than their atomic import request.
                    // Split before publishing a chunk that exceeds the common replay ceiling.
                    val replay = RestoreVersionRequest.state(model, batch, batchAllocations)
                    validateRestoreVersion(replay, requireNotNull(atomicCapabilities))
                } catch (error: RequestException) {
                    if (batch.size == 1) throw error
                    val middle = batch.size / 2
                    writeRecords(batch.subList(0, middle))
                    writeRecords(batch.subList(middle, batch.size))
                    return
                }
                request.writer.write(DataStoreStateBackupChunk(model.Meta.name, batch, batchAllocations))
            }
            suspend fun flush() {
                writeRecords(records.toList())
                records.clear()
                allocations.clear()
                bytes = 0
            }
            for (record in page.values) {
                validateStateRecord(record)
                if (previous?.let { (it.bytes compareTo record.key.bytes) >= 0 } == true) throw RequestException("State backup scan returned duplicate or unordered keys")
                previous = Key(record.key.bytes.copyOf())
                val encoded = codec.encode(model.Meta.name, record, request.options.maxChunkBytes)
                val recordAllocations = snapshotStateAllocations(model, readAllocations(model, record.key))
                val allocationBytes = stateAllocationBytes(recordAllocations)
                if (encoded.size.toLong() + allocationBytes > request.options.maxChunkBytes) throw RequestException("State record and allocation metadata exceed byte limit")
                if (bytes + encoded.size + allocationBytes > request.options.maxChunkBytes) flush()
                // Detach the exported values from backend-owned mutable buffers.
                records += codec.decode(model.Meta.name, encoded)
                allocations[Key(record.key.bytes)] = recordAllocations
                bytes += encoded.size + allocationBytes
            }
            flush()
            cursor = page.nextCursor
            if (page.values.isEmpty() && cursor != null) throw RequestException("State backup scan returned an empty continuation page")
        } while (cursor != null)
    }
    currentCoroutineContext().ensureActive()
    request.writer.complete()
    return StateBackupResponse(manifest)
}

internal fun validateStateRecord(record: ValuesWithMetaData<IsRootDataModel>) {
    if (record.firstVersion > record.lastVersion || record.lastVersion >= ULong.MAX_VALUE - 2uL) {
        throw RequestException("State backup record has invalid creation/change versions")
    }
}

/**
 * Streams into an empty, isolated target. Later reader/backend failures can leave earlier batches
 * applied. Publish the target only after this method and application validation succeed.
 * History must be disabled: this format contains no evidence for revisions before the baseline.
 */
suspend fun IsDataStore.restoreState(reader: DataStoreStateBackupReader): DataStoreRestoreResult {
    val target = stateBackupStore()
    if (keepAllVersions) throw RequestException("State restore requires history disabled; use a history archive to preserve revisions")
    val inputManifest = reader.manifest
    val manifest = inputManifest.copy(modelNames = inputManifest.modelNames.toList(), modelMajorVersions = inputManifest.modelMajorVersions.toMap())
    if (manifest.formatVersion != DATA_STORE_STATE_BACKUP_FORMAT_VERSION) throw RequestException("Unsupported state backup format ${manifest.formatVersion}")
    if (manifest.modelNames.distinct().size != manifest.modelNames.size || manifest.modelMajorVersions.keys != manifest.modelNames.toSet()) {
        throw RequestException("State backup manifest has duplicate models or incomplete schema versions")
    }
    val models = manifest.modelNames.associateWith { name ->
        val model = dataModelIdsByString[name]?.let(dataModelsById::get) ?: throw RequestException("State backup model `$name` is not registered")
        if (model.Meta.version.major != manifest.modelMajorVersions.getValue(name)) throw RequestException("State backup model `$name` has an incompatible major version")
        model
    }
    for (model in models.values) {
        if (execute(model.scan(limit = 1u, filterSoftDeleted = false, allowTableScan = true)).values.isNotEmpty()) throw RequestException("State restore target must be empty")
    }
    var modelIndex = -1
    var previous: Key<IsRootDataModel>? = null
    var restored = 0uL
    reader.read { chunk ->
        currentCoroutineContext().ensureActive()
        val index = manifest.modelNames.indexOf(chunk.modelName)
        if (index < 0 || index < modelIndex) throw RequestException("State backup contains undeclared or unordered models")
        if (index != modelIndex) previous = null
        modelIndex = index
        if (chunk.records.size > 1000) throw RequestException("State backup chunk exceeds record limit")
        val model = models.getValue(chunk.modelName)
        val detached = prepareStateBackupChunk(model, chunk)
        for (record in detached.records) {
            if (previous?.let { (it.bytes compareTo record.key.bytes) >= 0 } == true) throw RequestException("State backup contains duplicate or unordered keys")
            previous = record.key
        }
        if (detached.records.isNotEmpty()) target.restoreStateVersion(model, detached.records, detached.allocations)
        restored += detached.records.size.toULong()
    }
    return DataStoreRestoreResult(models.size, restored)
}

/** Validate and detach one atomic state batch; shared by local restore and transport adapters. */
fun prepareStateBackupChunk(model: IsRootDataModel, chunk: DataStoreStateBackupChunk): DataStoreStateBackupChunk {
    if (chunk.modelName != model.Meta.name || chunk.records.size > 1000) throw RequestException("Invalid state backup model or record count")
    val allocations = snapshotStateAllocationRecords(model, chunk.records.map { it.key }.toSet(), chunk.allocations)
    val codec = StateBackupCodec(mapOf(model.Meta.name to model))
    var bytes = 0
    var previous: Key<IsRootDataModel>? = null
    val records = chunk.records.map { record ->
        validateStateRecord(record)
        if (previous?.let { (it.bytes compareTo record.key.bytes) >= 0 } == true) throw RequestException("State backup contains duplicate or unordered keys")
        previous = record.key
        bytes += stateAllocationBytes(allocations[record.key].orEmpty())
        val encoded = codec.encode(chunk.modelName, record, MAX_ATOMIC_BYTES - bytes)
        bytes += encoded.size
        codec.decode(chunk.modelName, encoded)
    }
    return DataStoreStateBackupChunk(model.Meta.name, records, allocations)
}
