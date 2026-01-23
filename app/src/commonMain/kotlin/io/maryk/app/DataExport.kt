package io.maryk.app

import maryk.core.extensions.bytes.toVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.models.asValues
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.protobuf.WriteCache
import maryk.datastore.shared.IsDataStore
import maryk.file.File
import maryk.json.JsonWriter
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
) {
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

internal suspend fun exportModelDataToFolder(
    dataStore: IsDataStore,
    model: IsRootDataModel,
    format: DataExportFormat,
    folder: String,
) {
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
): String {
    val safeModel = sanitizeFilePart(modelName)
    val safeKey = sanitizeFilePart(keyText)
    return "$safeModel.$safeKey.${format.extension}"
}

private fun buildModelFileName(
    modelName: String,
    format: DataExportFormat,
): String {
    val safeModel = sanitizeFilePart(modelName)
    return "$safeModel.data.${format.extension}"
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
