package io.maryk.app

import maryk.core.extensions.bytes.toVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.models.asValues
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.Change
import maryk.core.query.changes.SetChange
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scan
import maryk.core.protobuf.WriteCache
import maryk.datastore.shared.IsDataStore
import maryk.file.File
import maryk.json.JsonWriter
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.properties.references.SetReference
import maryk.yaml.YamlWriter
import kotlin.math.min

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
        val requestContext = buildRequestContext(model)
        val change = loadFullChangesForKey(dataStore, model, key) ?: return
        val fileName = buildRowFileName(model.Meta.name, keyText, format, "versions")
        val path = joinPath(folder, fileName)
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
        val path = joinPath(folder, fileName)
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
) {
    if (includeVersionHistory) {
        exportModelVersionedDataToFolder(dataStore, model, format, folder)
    } else {
        val requestContext = buildRequestContext(model)
        val fileName = buildModelFileName(model.Meta.name, format)
        val path = joinPath(folder, fileName)
        val batchSize = 250u
        var startKey: Key<IsRootDataModel>? = null
        var includeStart = true
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
                    startKey = startKey,
                    includeStart = includeStart,
                    limit = batchSize,
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
            val nextKey = response.values.last().key
            if (response.values.size < batchSize.toInt()) break
            startKey = nextKey
            includeStart = false
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
        if (index < bytes.size) {
            bytes[index++] = byte
        }
    }, requestContext)
    return if (index == bytes.size) bytes else bytes.copyOf(min(index, bytes.size))
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
) {
    val requestContext = buildRequestContext(model)
    val fileName = buildModelFileName(model.Meta.name, format, "versions")
    val path = joinPath(folder, fileName)
    val batchSize = 250u
    var startKey: Key<IsRootDataModel>? = null
    var includeStart = true
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
                startKey = startKey,
                includeStart = includeStart,
                limit = batchSize,
                filterSoftDeleted = true,
                allowTableScan = true,
            )
        )
        if (response.values.isEmpty()) break
        response.values.forEach { record ->
            val change = loadFullChangesForKey(dataStore, model, record.key) ?: return@forEach
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
        val nextKey = response.values.last().key
        if (response.values.size < batchSize.toInt()) break
        startKey = nextKey
        includeStart = false
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
): DataObjectVersionedChange<IsRootDataModel>? {
    val changes = mutableListOf<VersionedChanges>()
    var fromVersion = 0uL
    var sortingKey: maryk.core.properties.types.Bytes? = null
    val maxVersions = 1000u

    while (true) {
        val response = runCatching {
            dataStore.execute(
                model.getChanges(
                    key,
                    fromVersion = fromVersion,
                    maxVersions = maxVersions,
                    filterSoftDeleted = false,
                )
            )
        }.getOrElse {
            dataStore.execute(
                model.getChanges(
                    key,
                    fromVersion = fromVersion,
                    maxVersions = 1u,
                    filterSoftDeleted = false,
                )
            )
        }
        val entry = response.changes.firstOrNull() ?: break
        if (sortingKey == null) sortingKey = entry.sortingKey
        if (entry.changes.isEmpty()) break
        changes += entry.changes.mapNotNull(::sanitizeVersionedChanges)
        if (entry.changes.size < maxVersions.toInt()) break
        fromVersion = entry.changes.last().version + 1uL
    }

    if (changes.isEmpty()) return null
    return DataObjectVersionedChange(
        key = key,
        sortingKey = sortingKey,
        changes = changes,
    )
}

private fun sanitizeVersionedChanges(versionedChanges: VersionedChanges): VersionedChanges? {
    val sanitized = versionedChanges.changes.mapNotNull(::sanitizeChange)
    if (sanitized.isEmpty()) return null
    return VersionedChanges(versionedChanges.version, sanitized)
}

private fun sanitizeChange(change: maryk.core.query.changes.IsChange): maryk.core.query.changes.IsChange? {
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
        if (index < bytes.size) {
            bytes[index++] = byte
        }
    }, requestContext)
    return if (index == bytes.size) bytes else bytes.copyOf(min(index, bytes.size))
}

private fun sanitizeFilePart(value: String): String {
    val trimmed = value.trim().ifBlank { "data" }
    return trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

private fun joinPath(folder: String, name: String): String {
    return if (folder.endsWith("/") || folder.endsWith("\\")) {
        folder + name
    } else {
        "$folder/$name"
    }
}
