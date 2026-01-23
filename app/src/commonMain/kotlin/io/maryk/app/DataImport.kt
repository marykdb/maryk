package io.maryk.app

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.add
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.file.File
import maryk.json.JsonReader
import maryk.json.JsonToken
import maryk.core.yaml.MarykYamlReader

enum class DataImportScope {
    SINGLE,
    MULTIPLE,
}

internal data class ImportResult(
    val imported: Int,
    val failed: Int,
)

internal suspend fun importDataFromFile(
    dataStore: IsDataStore,
    model: IsRootDataModel,
    format: DataExportFormat,
    scope: DataImportScope,
    path: String,
): ImportResult {
    val requestContext = RequestContext(
        DefinitionsContext(mutableMapOf(model.Meta.name to DataModelReference(model))),
        dataModel = model,
    )
    var imported = 0
    var failed = 0
    val batch = ArrayList<Pair<maryk.core.properties.types.Key<IsRootDataModel>, Values<IsRootDataModel>>>(100)

    suspend fun flushBatch() {
        if (batch.isEmpty()) return
        val response = dataStore.execute(model.add(*batch.toTypedArray()))
        val (ok, errors) = countStatuses(response.statuses)
        imported += ok
        failed += errors
        batch.clear()
    }

    suspend fun handleRecord(values: ObjectValues<ValuesWithMetaData<*>, ValuesWithMetaData.Companion>) {
        val record = ValuesWithMetaData(values)
        batch.add(record.key to record.values)
        if (batch.size >= 100) {
            flushBatch()
        }
    }

    when (format) {
        DataExportFormat.JSON -> readJsonRecords(path, requestContext, scope, ::handleRecord)
        DataExportFormat.YAML -> readYamlRecords(path, requestContext, scope, ::handleRecord)
        DataExportFormat.PROTO -> readProtoRecords(path, requestContext, scope, ::handleRecord)
    }

    flushBatch()
    return ImportResult(imported = imported, failed = failed)
}

internal fun detectImportFormatFromPath(path: String): DataExportFormat? {
    val extension = path.substringAfterLast('.', "").lowercase()
    if (extension.isNotEmpty()) {
        return when (extension) {
            "json" -> DataExportFormat.JSON
            "yaml", "yml" -> DataExportFormat.YAML
            "proto" -> DataExportFormat.PROTO
            else -> null
        }
    }
    val bytes = File.readBytes(path) ?: return null
    if (bytes.any { it == 0.toByte() }) return DataExportFormat.PROTO
    val text = bytes.decodeToString()
    val first = text.firstNonWhitespaceChar() ?: return null
    return if (first == '{' || first == '[') DataExportFormat.JSON else DataExportFormat.YAML
}

internal fun detectImportScopeFromPath(path: String, format: DataExportFormat): DataImportScope {
    return when (format) {
        DataExportFormat.JSON -> {
            val content = File.readText(path) ?: return DataImportScope.SINGLE
            val first = content.firstNonWhitespaceChar()
            if (first == '[') DataImportScope.MULTIPLE else DataImportScope.SINGLE
        }
        DataExportFormat.YAML -> {
            val content = File.readText(path) ?: return DataImportScope.SINGLE
            val docs = splitYamlDocuments(content)
            if (docs.size > 1) DataImportScope.MULTIPLE else DataImportScope.SINGLE
        }
        DataExportFormat.PROTO -> {
            val bytes = File.readBytes(path) ?: return DataImportScope.SINGLE
            detectProtoScope(bytes)
        }
    }
}

private suspend fun readJsonRecords(
    path: String,
    requestContext: RequestContext,
    scope: DataImportScope,
    onRecord: suspend (ObjectValues<ValuesWithMetaData<*>, ValuesWithMetaData.Companion>) -> Unit,
) {
    val content = File.readText(path) ?: throw IllegalArgumentException("File not found: $path")
    val iterator = content.iterator()
    val reader = JsonReader { if (iterator.hasNext()) iterator.nextChar() else Char.MIN_VALUE }
    when (scope) {
        DataImportScope.SINGLE -> {
            val values = ValuesWithMetaData.Serializer.readJson(reader, requestContext)
            onRecord(values)
        }
        DataImportScope.MULTIPLE -> {
            val token = reader.nextToken()
            if (token !is JsonToken.StartArray) {
                throw IllegalArgumentException("Expected JSON array for multiple records.")
            }
            while (true) {
                when (val next = reader.nextToken()) {
                    is JsonToken.EndArray -> return
                    is JsonToken.ArraySeparator -> continue
                    is JsonToken.StartObject -> {
                        val values = ValuesWithMetaData.Serializer.readJson(reader, requestContext)
                        onRecord(values)
                    }
                    else -> throw IllegalArgumentException("Unexpected JSON token: $next")
                }
            }
        }
    }
}

private suspend fun readYamlRecords(
    path: String,
    requestContext: RequestContext,
    scope: DataImportScope,
    onRecord: suspend (ObjectValues<ValuesWithMetaData<*>, ValuesWithMetaData.Companion>) -> Unit,
) {
    val content = File.readText(path) ?: throw IllegalArgumentException("File not found: $path")
    val documents = splitYamlDocuments(content)
    when (scope) {
        DataImportScope.SINGLE -> {
            val doc = documents.firstOrNull() ?: return
            val values = ValuesWithMetaData.Serializer.readJson(MarykYamlReader(doc), requestContext)
            onRecord(values)
        }
        DataImportScope.MULTIPLE -> {
            documents.forEach { doc ->
                val values = ValuesWithMetaData.Serializer.readJson(MarykYamlReader(doc), requestContext)
                onRecord(values)
            }
        }
    }
}

private fun splitYamlDocuments(content: String): List<String> {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return emptyList()
    val raw = trimmed.split(Regex("(?m)^---\\s*$"))
    return raw.map { it.trim() }.filter { it.isNotEmpty() }
}

private suspend fun readProtoRecords(
    path: String,
    requestContext: RequestContext,
    scope: DataImportScope,
    onRecord: suspend (ObjectValues<ValuesWithMetaData<*>, ValuesWithMetaData.Companion>) -> Unit,
) {
    val bytes = File.readBytes(path) ?: throw IllegalArgumentException("File not found: $path")
    when (scope) {
        DataImportScope.SINGLE -> {
            var index = 0
            val values = ValuesWithMetaData.Serializer.readProtoBuf(bytes.size, reader = { bytes[index++] }, context = requestContext)
            onRecord(values)
        }
        DataImportScope.MULTIPLE -> {
            var index = 0
            while (index < bytes.size) {
                val length = initIntByVar { bytes[index++] }
                val end = index + length
                if (end > bytes.size) {
                    throw IllegalArgumentException("Invalid proto length at byte $index.")
                }
                val values = ValuesWithMetaData.Serializer.readProtoBuf(length, reader = { bytes[index++] }, context = requestContext)
                onRecord(values)
                index = end
            }
        }
    }
}

private fun countStatuses(statuses: List<IsAddResponseStatus<IsRootDataModel>>): Pair<Int, Int> {
    var ok = 0
    var failed = 0
    statuses.forEach { status ->
        if (status is AddSuccess) ok += 1 else failed += 1
    }
    return ok to failed
}

private fun String.firstNonWhitespaceChar(): Char? = firstOrNull { !it.isWhitespace() }

private fun detectProtoScope(bytes: ByteArray): DataImportScope {
    val read = readVarInt(bytes, 0) ?: return DataImportScope.SINGLE
    val total = read.bytesRead + read.value
    return if (total <= bytes.size) DataImportScope.MULTIPLE else DataImportScope.SINGLE
}

private data class VarIntRead(
    val value: Int,
    val bytesRead: Int,
)

private fun readVarInt(bytes: ByteArray, startIndex: Int): VarIntRead? {
    var shift = 0
    var result = 0
    var index = startIndex
    while (index < bytes.size && shift < 32) {
        val b = bytes[index].toInt()
        result = result or ((b and 0x7F) shl shift)
        index += 1
        if (b and 0x80 == 0) {
            return VarIntRead(result, index - startIndex)
        }
        shift += 7
    }
    return null
}
