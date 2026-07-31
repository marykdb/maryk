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
        when (format) {
            DataExportFormat.JSON -> File.writeText(path, serializeVersionedToJson(change, requestContext))
            DataExportFormat.YAML -> File.writeText(path, serializeVersionedToYaml(change, requestContext))
            DataExportFormat.PROTO -> File.writeBytes(path, serializeVersionedToProto(change, requestContext))
        }
    } else {
        val response = dataStore.execute(
            model.get(key, filterSoftDeleted = false)
        )
        val record = response.values.firstOrNull() ?: return
        val fileName = buildRowFileName(model.Meta.name, keyText, format)
        val path = joinExportPath(folder, fileName)
        val requestContext = buildRequestContext(model)
        when (format) {
            DataExportFormat.JSON -> File.writeText(path, serializeRecordToJson(record, requestContext))
            DataExportFormat.YAML -> File.writeText(path, serializeRecordToYaml(record, requestContext))
            DataExportFormat.PROTO -> File.writeBytes(path, serializeRecordToProto(record, requestContext))
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
        val batchSize = 250u
        var cursor: ScanCursor? = null
        var hasAny = false
        var jsonFirst = true

        when (format) {
            DataExportFormat.JSON -> File.writeText(path, "[\n")
            DataExportFormat.YAML -> File.writeText(path, "")
            DataExportFormat.PROTO -> File.writeBytes(path, ByteArray(0))
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
                        File.appendText(path, prefix + json)
                        jsonFirst = false
                    }
                    DataExportFormat.YAML -> {
                        val yaml = serializeRecordToYaml(record, requestContext)
                        val prefix = if (hasAny) "\n---\n" else "---\n"
                        File.appendText(path, prefix + yaml)
                    }
                    DataExportFormat.PROTO -> {
                        val bytes = serializeRecordToProto(record, requestContext)
                        appendBytes(path, bytes.size.toVarBytes())
                        appendBytes(path, bytes)
                    }
                }
                hasAny = true
            }
            cursor = response.nextCursor ?: break
        }

        when (format) {
            DataExportFormat.JSON -> File.appendText(path, "\n]\n")
            DataExportFormat.YAML -> {
                if (!hasAny) {
                    File.writeText(path, "[]\n")
                } else {
                    File.appendText(path, "\n")
                }
            }
            DataExportFormat.PROTO -> Unit
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
    val batchSize = 250u
    var cursor: ScanCursor? = null
    var hasAny = false
    var jsonFirst = true

    when (format) {
        DataExportFormat.JSON -> File.writeText(path, "[\n")
        DataExportFormat.YAML -> File.writeText(path, "")
        DataExportFormat.PROTO -> File.writeBytes(path, ByteArray(0))
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
                    File.appendText(path, prefix + json)
                    jsonFirst = false
                }
                DataExportFormat.YAML -> {
                    val yaml = serializeVersionedToYaml(change, requestContext)
                    val prefix = if (hasAny) "\n---\n" else "---\n"
                    File.appendText(path, prefix + yaml)
                }
                DataExportFormat.PROTO -> {
                    val bytes = serializeVersionedToProto(change, requestContext)
                    appendBytes(path, bytes.size.toVarBytes())
                    appendBytes(path, bytes)
                }
            }
            hasAny = true
        }
        cursor = response.nextCursor ?: break
    }

    when (format) {
        DataExportFormat.JSON -> File.appendText(path, "\n]\n")
        DataExportFormat.YAML -> {
            if (!hasAny) {
                File.writeText(path, "[]\n")
            } else {
                File.appendText(path, "\n")
            }
        }
        DataExportFormat.PROTO -> Unit
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
    val normalized = value
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('.')
        .ifBlank { "data" }

    val reservedSafe = if (normalized.substringBefore('.').uppercase() in windowsReservedFileNames) {
        "_$normalized"
    } else {
        normalized
    }

    return reservedSafe
        .take(MAX_FILE_PART_LENGTH)
        .trimEnd('.')
        .ifBlank { "data" }
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
