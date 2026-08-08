package io.maryk.app.data

import maryk.core.extensions.bytes.toVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.models.asValues
import maryk.core.properties.types.Key
import maryk.core.properties.types.Bytes
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.Change
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scan
import maryk.core.query.requests.ScanCursor
import maryk.core.protobuf.WriteCache
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.captureSnapshotVersion
import maryk.datastore.shared.rethrowIfFatal
import maryk.file.File
import maryk.json.JsonWriter
import maryk.core.properties.references.SetReference
import maryk.core.query.pairs.ReferenceValuePair
import maryk.yaml.YamlWriter
import kotlin.uuid.Uuid

enum class DataExportFormat(
    val label: String,
    val extension: String,
) {
    JSON("JSON", "json"),
    YAML("YAML", "yaml"),
    PROTO("Proto", "proto"),
}

internal fun DataExportFormat.extensionsForImport(): List<String> = when (this) {
    DataExportFormat.JSON -> listOf("json")
    DataExportFormat.YAML -> listOf("yaml", "yml")
    DataExportFormat.PROTO -> listOf("proto")
}

internal suspend fun exportRowDataToFolder(
    dataStore: IsDataStore,
    model: IsRootDataModel,
    key: Key<IsRootDataModel>,
    keyText: String,
    format: DataExportFormat,
    folder: String,
    includeVersionHistory: Boolean = false,
) {
    if (includeVersionHistory) {
        val snapshotVersion = dataStore.captureSnapshotVersion()
        val requestContext = buildRequestContext(model)
        val change = loadFullChangesForKey(dataStore, model, key, snapshotVersion) ?: return
        val fileName = buildRowFileName(model.Meta.name, keyText, format, "versions")
        val path = joinExportPath(folder, fileName)
        exportToTemporaryPath(path) { temporaryPath ->
            when (format) {
                DataExportFormat.JSON -> File.writeText(temporaryPath, serializeVersionedToJson(change, requestContext))
                DataExportFormat.YAML -> File.writeText(temporaryPath, serializeVersionedToYaml(change, requestContext))
                DataExportFormat.PROTO -> File.writeBytes(temporaryPath, serializeVersionedToProto(change, requestContext))
            }
        }
    } else {
        val response = dataStore.execute(
            model.get(key, filterSoftDeleted = false)
        )
        val record = response.values.firstOrNull() ?: return
        val fileName = buildRowFileName(model.Meta.name, keyText, format)
        val path = joinExportPath(folder, fileName)
        val requestContext = buildRequestContext(model)
        exportToTemporaryPath(path) { temporaryPath ->
            when (format) {
                DataExportFormat.JSON -> File.writeText(temporaryPath, serializeRecordToJson(record, requestContext))
                DataExportFormat.YAML -> File.writeText(temporaryPath, serializeRecordToYaml(record, requestContext))
                DataExportFormat.PROTO -> File.writeBytes(temporaryPath, serializeRecordToProto(record, requestContext))
            }
        }
    }
}

internal suspend fun exportModelDataToFolder(
    dataStore: IsDataStore,
    model: IsRootDataModel,
    format: DataExportFormat,
    folder: String,
    includeVersionHistory: Boolean = false,
    snapshotVersion: ULong? = null,
) {
    val effectiveSnapshotVersion = snapshotVersion ?: if (dataStore.keepAllVersions) {
        dataStore.captureSnapshotVersion()
    } else {
        null
    }
    if (includeVersionHistory) {
        exportModelVersionedDataToFolder(dataStore, model, format, folder, effectiveSnapshotVersion)
    } else {
        val requestContext = buildRequestContext(model)
        val fileName = buildModelFileName(model.Meta.name, format)
        val path = joinExportPath(folder, fileName)
        exportToTemporaryPath(path) { temporaryPath ->
            val batchSize = 250u
            var cursor: ScanCursor? = null
            var hasAny = false
            var jsonFirst = true

            when (format) {
                DataExportFormat.JSON -> File.writeText(temporaryPath, "[\n")
                DataExportFormat.YAML -> File.writeText(temporaryPath, "")
                DataExportFormat.PROTO -> File.writeBytes(temporaryPath, ByteArray(0))
            }

            while (true) {
                val response = dataStore.execute(
                    model.scan(
                        cursor = cursor,
                        limit = batchSize,
                        toVersion = effectiveSnapshotVersion,
                        filterSoftDeleted = true,
                        allowTableScan = true,
                    )
                )
                if (response.values.isEmpty()) break
                response.values.forEach { record ->
                    when (format) {
                        DataExportFormat.JSON -> {
                            val json = serializeRecordToJson(record, requestContext)
                            val prefix = if (jsonFirst) "" else ",\n"
                            File.appendText(temporaryPath, prefix + json)
                            jsonFirst = false
                        }
                        DataExportFormat.YAML -> {
                            val yaml = serializeRecordToYaml(record, requestContext)
                            val prefix = if (hasAny) "\n---\n" else "---\n"
                            File.appendText(temporaryPath, prefix + yaml)
                        }
                        DataExportFormat.PROTO -> {
                            val bytes = serializeRecordToProto(record, requestContext)
                            appendBytes(temporaryPath, bytes.size.toVarBytes())
                            appendBytes(temporaryPath, bytes)
                        }
                    }
                    hasAny = true
                }
                cursor = response.nextCursor ?: break
            }

            when (format) {
                DataExportFormat.JSON -> File.appendText(temporaryPath, "\n]\n")
                DataExportFormat.YAML -> {
                    if (!hasAny) {
                        File.writeText(temporaryPath, "[]\n")
                    } else {
                        File.appendText(temporaryPath, "\n")
                    }
                }
                DataExportFormat.PROTO -> Unit
            }
        }
    }
}

private fun serializeRecordToJson(
    record: ValuesWithMetaData<IsRootDataModel>,
    requestContext: RequestContext,
): String {
    val metaValues = ValuesWithMetaData.asValues(record, requestContext)
    return buildString {
        val writer = JsonWriter(pretty = true) { append(it) }
        ValuesWithMetaData.Serializer.writeJson(metaValues, writer, requestContext)
    }
}

private fun serializeRecordToYaml(
    record: ValuesWithMetaData<IsRootDataModel>,
    requestContext: RequestContext,
): String {
    val metaValues = ValuesWithMetaData.asValues(record, requestContext)
    return buildString {
        val writer = YamlWriter { append(it) }
        ValuesWithMetaData.Serializer.writeJson(metaValues, writer, requestContext)
    }
}

private fun serializeRecordToProto(
    record: ValuesWithMetaData<IsRootDataModel>,
    requestContext: RequestContext,
): ByteArray {
    val metaValues = ValuesWithMetaData.asValues(record, requestContext)
    val cache = WriteCache()
    val length = ValuesWithMetaData.Serializer.calculateProtoBufLength(metaValues, cache, requestContext)
    val bytes = ByteArray(length)
    var index = 0
    ValuesWithMetaData.Serializer.writeProtoBuf(metaValues, cache, { byte ->
        bytes[index++] = byte
    }, requestContext)
    check(index == bytes.size) { "Proto length mismatch: wrote $index of ${bytes.size} bytes." }
    return bytes
}

private fun buildRowFileName(
    modelName: String,
    keyText: String,
    format: DataExportFormat,
    suffix: String? = null,
): String {
    val safeModel = sanitizeFilePart(modelName)
    val safeKey = sanitizeFilePart(keyText)
    val extra = suffix?.let { ".$it" }.orEmpty()
    return "$safeModel.$safeKey$extra.${format.extension}"
}

private fun buildModelFileName(
    modelName: String,
    format: DataExportFormat,
    suffix: String? = null,
): String {
    val safeModel = sanitizeFilePart(modelName)
    val extra = suffix?.let { ".$it" }.orEmpty()
    return "$safeModel.data$extra.${format.extension}"
}

private suspend fun exportModelVersionedDataToFolder(
    dataStore: IsDataStore,
    model: IsRootDataModel,
    format: DataExportFormat,
    folder: String,
    snapshotVersion: ULong?,
) {
    val requestContext = buildRequestContext(model)
    val fileName = buildModelFileName(model.Meta.name, format, "versions")
    val path = joinExportPath(folder, fileName)
    exportToTemporaryPath(path) { temporaryPath ->
        val batchSize = 250u
        var cursor: ScanCursor? = null
        var hasAny = false
        var jsonFirst = true

        when (format) {
            DataExportFormat.JSON -> File.writeText(temporaryPath, "[\n")
            DataExportFormat.YAML -> File.writeText(temporaryPath, "")
            DataExportFormat.PROTO -> File.writeBytes(temporaryPath, ByteArray(0))
        }

        while (true) {
            val response = dataStore.execute(
                model.scan(
                    cursor = cursor,
                    limit = batchSize,
                    toVersion = snapshotVersion,
                    filterSoftDeleted = false,
                    allowTableScan = true,
                )
            )
            if (response.values.isEmpty()) break
            response.values.forEach { record ->
                val change = loadFullChangesForKey(dataStore, model, record.key, snapshotVersion) ?: return@forEach
                when (format) {
                    DataExportFormat.JSON -> {
                        val json = serializeVersionedToJson(change, requestContext)
                        val prefix = if (jsonFirst) "" else ",\n"
                        File.appendText(temporaryPath, prefix + json)
                        jsonFirst = false
                    }
                    DataExportFormat.YAML -> {
                        val yaml = serializeVersionedToYaml(change, requestContext)
                        val prefix = if (hasAny) "\n---\n" else "---\n"
                        File.appendText(temporaryPath, prefix + yaml)
                    }
                    DataExportFormat.PROTO -> {
                        val bytes = serializeVersionedToProto(change, requestContext)
                        appendBytes(temporaryPath, bytes.size.toVarBytes())
                        appendBytes(temporaryPath, bytes)
                    }
                }
                hasAny = true
            }
            cursor = response.nextCursor ?: break
        }

        when (format) {
            DataExportFormat.JSON -> File.appendText(temporaryPath, "\n]\n")
            DataExportFormat.YAML -> {
                if (!hasAny) {
                    File.writeText(temporaryPath, "[]\n")
                } else {
                    File.appendText(temporaryPath, "\n")
                }
            }
            DataExportFormat.PROTO -> Unit
        }
    }
}

private suspend fun exportToTemporaryPath(
    path: String,
    write: suspend (temporaryPath: String) -> Unit,
) {
    val temporaryPath = "$path.${Uuid.random()}.tmp"
    try {
        write(temporaryPath)
        File.moveReplace(temporaryPath, path)
    } finally {
        File.delete(temporaryPath)
    }
}

private suspend fun loadFullChangesForKey(
    dataStore: IsDataStore,
    model: IsRootDataModel,
    key: Key<IsRootDataModel>,
    toVersion: ULong? = null,
): DataObjectVersionedChange<IsRootDataModel>? {
    val complete = loadCompleteChangesForKey(dataStore, model, key, toVersion) ?: return null
    return complete.copy(
        changes = complete.changes.mapNotNull(::sanitizeVersionedChanges),
    )
}

internal suspend fun loadCompleteChangesForKey(
    dataStore: IsDataStore,
    model: IsRootDataModel,
    key: Key<IsRootDataModel>,
    toVersion: ULong? = null,
): DataObjectVersionedChange<IsRootDataModel>? {
    val changesByVersion = mutableMapOf<ULong, VersionedChanges>()
    var sortingKey: Bytes? = null
    var upperVersion = toVersion
    while (true) {
        val response = try {
            dataStore.execute(
                model.getChanges(
                    key,
                    toVersion = upperVersion,
                    maxVersions = HISTORY_PAGE_SIZE,
                    filterSoftDeleted = false,
                )
            )
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            return loadCompleteChangesForwardOneAtATime(dataStore, model, key, toVersion)
        }
        val entry = response.changes.firstOrNull() ?: break
        if (sortingKey == null) sortingKey = entry.sortingKey
        if (entry.changes.isEmpty()) break
        val previousSize = changesByVersion.size
        entry.changes.forEach { changesByVersion[it.version] = it }
        val nonCreationChanges = entry.changes.filterNot { ObjectCreate in it.changes }
        if (nonCreationChanges.size < HISTORY_PAGE_SIZE.toInt()) break

        val oldestVersion = nonCreationChanges.first().version
        if (upperVersion != null && oldestVersion >= upperVersion && changesByVersion.size == previousSize) break
        upperVersion = oldestVersion
    }

    if (changesByVersion.isEmpty()) return null
    replaceNormalizedCreation(dataStore, model, key, changesByVersion)
    return DataObjectVersionedChange(
        key = key,
        sortingKey = sortingKey,
        changes = changesByVersion.values.sortedBy { it.version },
    )
}

private suspend fun loadCompleteChangesForwardOneAtATime(
    dataStore: IsDataStore,
    model: IsRootDataModel,
    key: Key<IsRootDataModel>,
    toVersion: ULong?,
): DataObjectVersionedChange<IsRootDataModel>? {
    val changesByVersion = mutableMapOf<ULong, VersionedChanges>()
    var sortingKey: Bytes? = null
    var fromVersion = 0uL
    while (true) {
        val response = dataStore.execute(
            model.getChanges(
                key,
                fromVersion = fromVersion,
                toVersion = toVersion,
                maxVersions = 1u,
                filterSoftDeleted = false,
            )
        )
        val entry = response.changes.firstOrNull() ?: break
        if (sortingKey == null) sortingKey = entry.sortingKey
        if (entry.changes.isEmpty()) break
        val previousSize = changesByVersion.size
        entry.changes.forEach { changesByVersion[it.version] = it }
        val lastVersion = entry.changes.last().version
        if (changesByVersion.size == previousSize || lastVersion == ULong.MAX_VALUE) break
        fromVersion = lastVersion + 1uL
    }
    if (changesByVersion.isEmpty()) return null
    replaceNormalizedCreation(dataStore, model, key, changesByVersion)
    return DataObjectVersionedChange(
        key = key,
        sortingKey = sortingKey,
        changes = changesByVersion.values.sortedBy { it.version },
    )
}

private suspend fun replaceNormalizedCreation(
    dataStore: IsDataStore,
    model: IsRootDataModel,
    key: Key<IsRootDataModel>,
    changesByVersion: MutableMap<ULong, VersionedChanges>,
) {
    val creationVersion = changesByVersion.values
        .firstOrNull { ObjectCreate in it.changes }
        ?.version
        ?: return
    if (changesByVersion.values.none { ObjectCreate !in it.changes }) return
    val values = dataStore.execute(
        model.get(
            key,
            toVersion = creationVersion,
            filterSoftDeleted = false,
        )
    ).values.singleOrNull()?.values
        ?: return
    changesByVersion[creationVersion] = VersionedChanges(
        creationVersion,
        listOf(ObjectCreate, *values.toChanges()),
    )
}

private const val HISTORY_PAGE_SIZE = 1000u

private fun sanitizeVersionedChanges(versionedChanges: VersionedChanges): VersionedChanges? {
    val sanitized = versionedChanges.changes.mapNotNull(::sanitizeChange)
    if (sanitized.isEmpty()) return null
    return VersionedChanges(versionedChanges.version, sanitized)
}

private fun sanitizeChange(change: IsChange): IsChange? {
    return when (change) {
        is Change -> {
            val filtered = change.referenceValuePairs.filterNot { pair ->
                pair is ReferenceValuePair<*> && pair.value == Unit
            }
            if (filtered.isEmpty()) null else Change(*filtered.toTypedArray())
        }
        is SetChange -> {
            val filtered = change.setValueChanges.filter { it.reference is SetReference<*, *> }
            if (filtered.isEmpty()) null else SetChange(*filtered.toTypedArray())
        }
        else -> change
    }
}

private fun serializeVersionedToJson(
    change: DataObjectVersionedChange<IsRootDataModel>,
    requestContext: RequestContext,
): String {
    val values = DataObjectVersionedChange.asValues(change, requestContext)
    return buildString {
        val writer = JsonWriter(pretty = true) { append(it) }
        DataObjectVersionedChange.Serializer.writeJson(values, writer, requestContext)
    }
}

private fun serializeVersionedToYaml(
    change: DataObjectVersionedChange<IsRootDataModel>,
    requestContext: RequestContext,
): String {
    val values = DataObjectVersionedChange.asValues(change, requestContext)
    return buildString {
        val writer = YamlWriter { append(it) }
        DataObjectVersionedChange.Serializer.writeJson(values, writer, requestContext)
    }
}

private fun serializeVersionedToProto(
    change: DataObjectVersionedChange<IsRootDataModel>,
    requestContext: RequestContext,
): ByteArray {
    val values = DataObjectVersionedChange.asValues(change, requestContext)
    val cache = WriteCache()
    val length = DataObjectVersionedChange.Serializer.calculateProtoBufLength(values, cache, requestContext)
    val bytes = ByteArray(length)
    var index = 0
    DataObjectVersionedChange.Serializer.writeProtoBuf(values, cache, { byte ->
        bytes[index++] = byte
    }, requestContext)
    check(index == bytes.size) { "Proto length mismatch: wrote $index of ${bytes.size} bytes." }
    return bytes
}

private const val MAX_FILE_PART_LENGTH = 120

private val windowsReservedFileNames = setOf(
    "CON",
    "PRN",
    "AUX",
    "NUL",
    "COM1",
    "COM2",
    "COM3",
    "COM4",
    "COM5",
    "COM6",
    "COM7",
    "COM8",
    "COM9",
    "LPT1",
    "LPT2",
    "LPT3",
    "LPT4",
    "LPT5",
    "LPT6",
    "LPT7",
    "LPT8",
    "LPT9",
)

internal fun sanitizeFilePart(value: String): String {
    val encoded = buildString {
        value.trim().forEach { char ->
            append(if (char.isAsciiFileNameCharacter()) char.toString() else "_${char.code.toString(16)}_")
        }
    }
    val normalized = encoded
        .trim('.')
        .ifBlank { "data" }

    val reservedSafe = if (normalized.substringBefore('.').uppercase() in windowsReservedFileNames) {
        "_$normalized"
    } else {
        normalized
    }

    val disambiguated = if (value.isNotEmpty() && reservedSafe != value) {
        "$reservedSafe-${value.fileNameHash()}"
    } else {
        reservedSafe
    }

    return disambiguated
        .take(MAX_FILE_PART_LENGTH - if (disambiguated.length > MAX_FILE_PART_LENGTH) 17 else 0)
        .trimEnd('.')
        .ifBlank { "data" }
        .let { shortened ->
            if (disambiguated.length > MAX_FILE_PART_LENGTH) "$shortened-${value.fileNameHash()}" else shortened
        }
}

private fun Char.isAsciiFileNameCharacter(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '.' || this == '_' || this == '-'

private fun String.fileNameHash(): String {
    var hash = 0xcbf29ce484222325uL
    forEach { char ->
        hash = (hash xor char.code.toULong()) * 0x100000001b3uL
    }
    return hash.toString(16)
}

internal fun joinExportPath(folder: String, name: String): String {
    if (folder.isEmpty()) return name

    val normalized = folder.trimEnd('/', '\\')
    val base = when {
        normalized.isEmpty() -> folder.first().toString()
        normalized.length == 2 && normalized[1] == ':' && folder.length > 2 &&
            (folder[2] == '/' || folder[2] == '\\') -> "$normalized/"
        else -> normalized
    }

    return if (base.endsWith("/") || base.endsWith("\\")) {
        base + name
    } else {
        "$base/$name"
    }
}
