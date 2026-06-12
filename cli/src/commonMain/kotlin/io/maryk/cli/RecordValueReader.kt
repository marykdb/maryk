package io.maryk.cli

import maryk.core.models.IsRootDataModel
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.Values
import maryk.core.yaml.MarykYamlReader
import maryk.file.File
import maryk.json.IsJsonLikeReader
import maryk.json.JsonToken
import maryk.json.JsonReader
import maryk.datastore.shared.rethrowIfFatal

internal data class LoadedRecordValues(
    val values: Values<IsRootDataModel>,
    val meta: ValuesWithMetaData<IsRootDataModel>? = null,
)

internal sealed class ParsedRecordValues {
    data class Single(val values: Values<IsRootDataModel>) : ParsedRecordValues()
    data class Multi(val values: List<Values<IsRootDataModel>>) : ParsedRecordValues()
}

internal fun readRecordValues(
    dataModel: IsRootDataModel,
    path: String,
    format: SaveFormat,
    useMeta: Boolean,
): LoadedRecordValues {
    return if (useMeta) {
        readRecordMetaValues(dataModel, path, format)
    } else {
        LoadedRecordValues(readRecordDataValues(dataModel, path, format))
    }
}

internal fun readRecordValuesInput(
    dataModel: IsRootDataModel,
    path: String,
    format: SaveFormat,
): ParsedRecordValues {
    @Suppress("UNCHECKED_CAST")
    val serializer = dataModel.Serializer as IsDataModelSerializer<
        Values<IsRootDataModel>,
        IsRootDataModel,
        IsPropertyContext
    >

    return when (format) {
        SaveFormat.YAML -> {
            val content = readTextInput(path)
            val reader = MarykYamlReader(content)
            val token = if (reader.currentToken == JsonToken.StartDocument) reader.nextToken() else reader.currentToken
            when (token) {
                is JsonToken.StartArray -> ParsedRecordValues.Multi(readValuesList(reader, serializer))
                is JsonToken.StartObject -> ParsedRecordValues.Single(
                    serializer.readJson(reader, null).also { ensureJsonInputEnded(reader) }
                )
                else -> throw IllegalArgumentException("Expected object or list at top level.")
            }
        }
        SaveFormat.JSON -> {
            val content = readTextInput(path)
            val iterator = content.iterator()
            val reader = JsonReader { if (iterator.hasNext()) iterator.nextChar() else null }
            val token = if (reader.currentToken == JsonToken.StartDocument) reader.nextToken() else reader.currentToken
            when (token) {
                is JsonToken.StartArray -> ParsedRecordValues.Multi(readValuesList(reader, serializer))
                is JsonToken.StartObject -> ParsedRecordValues.Single(
                    serializer.readJson(reader, null).also { ensureJsonInputEnded(reader) }
                )
                else -> throw IllegalArgumentException("Expected object or list at top level.")
            }
        }
        SaveFormat.PROTO -> ParsedRecordValues.Single(readRecordDataValues(dataModel, path, format))
        SaveFormat.KOTLIN -> throw IllegalArgumentException("Kotlin input is not supported.")
    }
}

private fun readRecordDataValues(
    dataModel: IsRootDataModel,
    path: String,
    format: SaveFormat,
): Values<IsRootDataModel> {
    @Suppress("UNCHECKED_CAST")
    val serializer = dataModel.Serializer as IsDataModelSerializer<
        Values<IsRootDataModel>,
        IsRootDataModel,
        IsPropertyContext
    >

    return when (format) {
        SaveFormat.YAML -> {
            val content = readTextInput(path)
            serializer.readJson(MarykYamlReader(content), null)
        }
        SaveFormat.JSON -> {
            val content = readTextInput(path)
            val iterator = content.iterator()
            val reader = JsonReader { if (iterator.hasNext()) iterator.nextChar() else null }
            serializer.readJson(reader, null).also { ensureJsonInputEnded(reader) }
        }
        SaveFormat.PROTO -> {
            val bytes = readBytesInput(path)
            readProtoInputPayload(bytes, "record") { reader ->
                serializer.readProtoBuf(bytes.size, reader = reader, context = null)
            }
        }
        SaveFormat.KOTLIN -> throw IllegalArgumentException("Kotlin input is not supported.")
    }
}

private fun readValuesList(
    reader: IsJsonLikeReader,
    serializer: IsDataModelSerializer<
        Values<IsRootDataModel>,
        IsRootDataModel,
        IsPropertyContext
    >
): List<Values<IsRootDataModel>> {
    if (reader.currentToken == JsonToken.StartDocument) {
        reader.nextToken()
    }

    if (reader.currentToken !is JsonToken.StartArray) {
        throw IllegalArgumentException("Expected a list of objects at the top level.")
    }

    val values = mutableListOf<Values<IsRootDataModel>>()
    reader.nextToken()

    while (true) {
        when (val token = reader.currentToken) {
            is JsonToken.EndArray -> {
                ensureJsonInputEnded(reader)
                break
            }
            is JsonToken.StartObject -> {
                values.add(serializer.readJson(reader, null))
                reader.nextToken()
            }
            is JsonToken.StartArray -> {
                throw IllegalArgumentException("Nested lists are not supported.")
            }
            is JsonToken.Value<*> -> {
                throw IllegalArgumentException("Expected object in list, found value.")
            }
            is JsonToken.EndDocument -> throw IllegalArgumentException("Unexpected end of input in list.")
            else -> {
                throw IllegalArgumentException("Expected object or end of list, found ${token::class.simpleName}.")
            }
        }
    }

    return values
}

internal fun ensureJsonInputEnded(reader: IsJsonLikeReader) {
    when (val token = reader.nextToken()) {
        is JsonToken.EndDocument -> Unit
        else -> throw IllegalArgumentException("Unexpected JSON token after input: $token")
    }
}

private fun readRecordMetaValues(
    dataModel: IsRootDataModel,
    path: String,
    format: SaveFormat,
): LoadedRecordValues {
    val requestContext = RequestContext(
        DefinitionsContext(mutableMapOf(dataModel.Meta.name to DataModelReference(dataModel))),
        dataModel = dataModel,
    )

    val metaValues = when (format) {
        SaveFormat.YAML -> {
            val content = readTextInput(path)
            ValuesWithMetaData.Serializer.readJson(MarykYamlReader(content), requestContext)
        }
        SaveFormat.JSON -> {
            val content = readTextInput(path)
            val iterator = content.iterator()
            val reader = JsonReader { if (iterator.hasNext()) iterator.nextChar() else null }
            ValuesWithMetaData.Serializer.readJson(reader, requestContext).also { ensureJsonInputEnded(reader) }
        }
        SaveFormat.PROTO -> {
            val bytes = readBytesInput(path)
            readProtoInputPayload(bytes, "record metadata") { reader ->
                ValuesWithMetaData.Serializer.readProtoBuf(bytes.size, reader = reader, context = requestContext)
            }
        }
        SaveFormat.KOTLIN -> throw IllegalArgumentException("Kotlin input is not supported.")
    }

    val meta = ValuesWithMetaData(metaValues)
    return LoadedRecordValues(meta.values, meta)
}

private fun readTextInput(path: String): String {
    return if (path == "-") {
        readStdinText()
    } else {
        readBytesInput(path).decodeToString()
    }
}

private fun readBytesInput(path: String): ByteArray {
    return if (path == "-") {
        readStdinBytes()
    } else {
        ensureInputFileSize(path)
        val bytes = File.readBytes(path) ?: throw IllegalArgumentException("File not found: $path")
        if (bytes.size > MAX_STDIN_BYTES) {
            throw IllegalArgumentException("input file exceeds max size: ${bytes.size} > $MAX_STDIN_BYTES bytes")
        }
        bytes
    }
}

private fun ensureInputFileSize(path: String) {
    val size = File.size(path) ?: return
    if (size > MAX_STDIN_BYTES) {
        throw IllegalArgumentException("input file exceeds max size: $size > $MAX_STDIN_BYTES bytes")
    }
}

internal inline fun <T> readProtoInputPayload(
    bytes: ByteArray,
    label: String,
    read: (() -> Byte) -> T,
): T {
    var index = 0
    val value = try {
        read {
            if (index >= bytes.size) {
                throw IllegalArgumentException("Invalid proto $label: attempted to read past ${bytes.size} bytes.")
            }
            bytes[index++]
        }
    } catch (error: Throwable) {
        error.rethrowIfFatal()
        throw IllegalArgumentException("Invalid proto $label.", error)
    }
    if (index != bytes.size) {
        throw IllegalArgumentException("Invalid proto $label: consumed $index of ${bytes.size} bytes.")
    }
    return value
}
