package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.requests.ScanCursor
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scan
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.InitialChangesUpdate

const val DATA_STORE_BACKUP_FORMAT_VERSION = 2u
/** Maximum complete version history retained for one record during backup or export. */
const val DEFAULT_HISTORY_VERSIONS_PER_RECORD = 10_000u

/** Metadata written before the chunks of a portable logical backup. */
data class DataStoreBackupManifest(
    val formatVersion: UInt = DATA_STORE_BACKUP_FORMAT_VERSION,
    val snapshotVersion: ULong,
    val modelNames: List<String>,
    /** Major schema versions required to safely decode each model's stored references. */
    val modelMajorVersions: Map<String, UShort> = emptyMap(),
    val includesVersionHistory: Boolean = true,
)

/** One bounded model-specific block in a portable logical backup. */
data class DataStoreBackupChunk(
    val modelName: String,
    val records: List<DataObjectVersionedChange<IsRootDataModel>>,
)

/**
 * Streaming destination. Implementations can write files, object storage, or a network stream.
 *
 * A backup is complete only after [complete] returns. Writers should keep incomplete output
 * unpublished and use durable storage, checksums, and atomic publication where required.
 */
interface DataStoreBackupWriter {
    suspend fun begin(manifest: DataStoreBackupManifest)
    suspend fun write(chunk: DataStoreBackupChunk)
    suspend fun complete()
}

/** Streaming source for a backup previously written through [DataStoreBackupWriter]. */
interface DataStoreBackupReader {
    val manifest: DataStoreBackupManifest
    suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit)
}

data class DataStoreRestoreResult(
    val models: Int,
    val records: ULong,
)

/**
 * Captures a read boundary for stores with historic reads.
 *
 * Stores must supply an authoritative watermark. Callers may alternatively pass
 * an explicit cluster version to [backup].
 */
suspend fun IsDataStore.captureSnapshotVersion(): ULong {
    if (!keepAllVersions) {
        throw RequestException("Point-in-time snapshots require keepAllVersions")
    }
    val provider = this as? SnapshotVersionProvider
        ?: throw RequestException(
            "Data store does not provide an authoritative snapshot version; " +
                "pass an explicit cluster-authoritative snapshotVersion"
        )
    return provider.captureSnapshotVersion()
}

/**
 * Streams a versioned, point-in-time logical backup.
 *
 * The format is additive and versioned. Records are emitted in bounded chunks and
 * scans resume through opaque cursors.
 */
suspend fun IsDataStore.backup(
    writer: DataStoreBackupWriter,
    snapshotVersion: ULong? = null,
    batchSize: UInt = 250u,
): DataStoreBackupManifest = backup(
    writer,
    snapshotVersion,
    batchSize,
    DEFAULT_HISTORY_VERSIONS_PER_RECORD,
)

/**
 * Streams a versioned, point-in-time logical backup with a bound for one record's history.
 *
 * A record exceeding [maxHistoryVersionsPerRecord] stops the incomplete backup before it can
 * silently omit older versions.
 */
suspend fun IsDataStore.backup(
    writer: DataStoreBackupWriter,
    snapshotVersion: ULong?,
    batchSize: UInt,
    maxHistoryVersionsPerRecord: UInt,
): DataStoreBackupManifest {
    require(batchSize > 0u) { "Backup batch size must be at least 1" }
    require(maxHistoryVersionsPerRecord > 0u) { "Backup history limit must be at least 1" }
    if (!keepAllVersions) {
        throw RequestException("Portable point-in-time backup requires keepAllVersions")
    }

    val effectiveSnapshotVersion = snapshotVersion ?: captureSnapshotVersion()
    val models = dataModelsById.entries.sortedBy { it.key }.map { it.value }
    val manifest = DataStoreBackupManifest(
        snapshotVersion = effectiveSnapshotVersion,
        modelNames = models.map { it.Meta.name },
        modelMajorVersions = models.associate { it.Meta.name to it.Meta.version.major },
    )
    writer.begin(manifest)

    for (model in models) {
        var cursor: ScanCursor? = null
        while (true) {
            val page = execute(
                model.scan(
                    cursor = cursor,
                    limit = batchSize,
                    toVersion = effectiveSnapshotVersion,
                    filterSoftDeleted = false,
                    allowTableScan = true,
                )
            )
            if (page.values.isEmpty()) break

            val records = mutableListOf<DataObjectVersionedChange<IsRootDataModel>>()
            for (value in page.values) {
                readCompleteChanges(
                    model,
                    value.key,
                    effectiveSnapshotVersion,
                    maxHistoryVersionsPerRecord,
                )?.let(records::add)
            }
            if (records.isNotEmpty()) {
                writer.write(DataStoreBackupChunk(model.Meta.name, records))
            }
            cursor = page.nextCursor ?: break
        }
    }

    writer.complete()
    return manifest
}

/**
 * Restores versioned backup chunks through the normal replication path.
 *
 * By default the addressed models must be empty, avoiding accidental merges.
 * Restore is streaming rather than globally transactional: if reading or applying a later chunk
 * fails, earlier chunks remain applied. Restore into an empty disposable store, then publish or
 * replace that store only after this function succeeds.
 */
suspend fun IsDataStore.restore(
    reader: DataStoreBackupReader,
    requireEmpty: Boolean = true,
): DataStoreRestoreResult {
    if (!keepAllVersions) {
        throw RequestException("Portable backup restore requires keepAllVersions")
    }
    val manifest = reader.manifest
    if (manifest.formatVersion != DATA_STORE_BACKUP_FORMAT_VERSION) {
        throw RequestException("Unsupported backup format version ${manifest.formatVersion}")
    }
    if (!manifest.includesVersionHistory) {
        throw RequestException("Backup does not contain version history")
    }
    if (manifest.modelNames.size != manifest.modelNames.toSet().size) {
        throw RequestException("Backup manifest contains duplicate model names")
    }
    if (manifest.modelMajorVersions.keys != manifest.modelNames.toSet()) {
        throw RequestException("Backup manifest has incomplete model version information")
    }

    val models = manifest.modelNames.associateWith { modelName ->
        val modelId = dataModelIdsByString[modelName]
            ?: throw RequestException("Backup model `$modelName` is not registered")
        dataModelsById[modelId]
            ?: throw RequestException("Backup model `$modelName` is not registered")
    }.also { registeredModels ->
        registeredModels.forEach { (modelName, model) ->
            if (model.Meta.version.major != manifest.modelMajorVersions.getValue(modelName)) {
                throw RequestException(
                    "Backup model `$modelName` requires major version " +
                        "${manifest.modelMajorVersions.getValue(modelName)}, but target has ${model.Meta.version.major}"
                )
            }
        }
    }

    if (requireEmpty) {
        for ((name, model) in models) {
            if (execute(model.scan(limit = 1u, filterSoftDeleted = false, allowTableScan = true)).values.isNotEmpty()) {
                throw RequestException("Restore target model `$name` is not empty")
            }
        }
    }

    var restored = 0uL
    reader.read { chunk ->
        val model = models[chunk.modelName]
            ?: throw RequestException("Backup chunk references undeclared model `${chunk.modelName}`")
        val changes = chunk.records
        validateBackupRecords(chunk.modelName, changes, manifest.snapshotVersion)
        val response = processUpdate(
            UpdateResponse(
                dataModel = model,
                update = InitialChangesUpdate(manifest.snapshotVersion, changes),
            )
        )
        val result = response.result as? AddOrChangeResponse<*>
            ?: throw RequestException(
                "Could not restore `${chunk.modelName}`: unexpected ${response.result::class.simpleName} response"
            )
        val expectedStatuses = changes.sumOf { it.changes.size }
        if (result.statuses.size != expectedStatuses) {
            throw RequestException(
                "Could not restore `${chunk.modelName}`: expected $expectedStatuses statuses, " +
                    "received ${result.statuses.size}"
            )
        }
        val failures = result.statuses
            .filterNot { it is AddSuccess<*> || it is ChangeSuccess<*> }
        if (failures.isNotEmpty()) {
            throw RequestException("Could not restore `${chunk.modelName}`: ${failures.joinToString()}")
        }
        restored += changes.size.toULong()
    }

    return DataStoreRestoreResult(models.size, restored)
}

private fun validateBackupRecords(
    modelName: String,
    records: List<DataObjectVersionedChange<IsRootDataModel>>,
    snapshotVersion: ULong,
) {
    records.forEach { record ->
        if (record.changes.isEmpty()) {
            throw RequestException("Backup record for `$modelName` has no version history")
        }
        if (ObjectCreate !in record.changes.first().changes ||
            record.changes.drop(1).any { ObjectCreate in it.changes }
        ) {
            throw RequestException("Backup record for `$modelName` has invalid creation history")
        }
        if (record.changes.any { it.version >= snapshotVersion }) {
            throw RequestException("Backup record for `$modelName` exceeds its snapshot version")
        }
        if (record.changes.zipWithNext().any { (previous, next) -> previous.version >= next.version }) {
            throw RequestException("Backup record for `$modelName` has unordered or duplicate versions")
        }
    }
}

private suspend fun IsDataStore.readCompleteChanges(
    model: IsRootDataModel,
    key: Key<IsRootDataModel>,
    snapshotVersion: ULong,
    maxHistoryVersions: UInt,
): DataObjectVersionedChange<IsRootDataModel>? {
    val changesByVersion = mutableMapOf<ULong, VersionedChanges>()
    var toVersion = snapshotVersion

    while (true) {
        val response = execute(
            model.getChanges(
                key,
                toVersion = toVersion,
                maxVersions = BACKUP_HISTORY_PAGE_SIZE,
                filterSoftDeleted = false,
            )
        )
        val record = response.changes.firstOrNull() ?: break
        if (record.changes.isEmpty()) break
        val previousSize = changesByVersion.size
        record.changes.forEach {
            changesByVersion[it.version] = it
            if (changesByVersion.size.toUInt() > maxHistoryVersions) {
                throw RequestException(
                    "Backup record history exceeds the configured limit of $maxHistoryVersions versions"
                )
            }
        }
        val nonCreationChanges = record.changes.filterNot { ObjectCreate in it.changes }
        if (nonCreationChanges.size < BACKUP_HISTORY_PAGE_SIZE.toInt()) break

        val oldestVersion = nonCreationChanges.first().version
        if (oldestVersion >= toVersion && changesByVersion.size == previousSize) break
        toVersion = oldestVersion
    }

    val creationVersion = changesByVersion.values
        .firstOrNull { ObjectCreate in it.changes }
        ?.version
    if (creationVersion != null) {
        val values = execute(
            model.get(
                key,
                toVersion = creationVersion,
                filterSoftDeleted = false,
            )
        ).values.singleOrNull()?.values
            ?: throw RequestException("Could not read creation values for backup record")
        changesByVersion[creationVersion] = VersionedChanges(
            creationVersion,
            listOf(ObjectCreate, *values.toChanges()),
        )
    }

    return changesByVersion.values.sortedBy { it.version }.takeIf { it.isNotEmpty() }?.let {
        DataObjectVersionedChange(key = key, changes = it)
    }
}

private const val BACKUP_HISTORY_PAGE_SIZE = 1000u
