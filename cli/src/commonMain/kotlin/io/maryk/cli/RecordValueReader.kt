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
import maryk.json.JsonReader

internal data class LoadedRecordValues(
    val values: Values<IsRootDataModel>,
    val meta: ValuesWithMetaData<IsRootDataModel>? = null,
)

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
            val content = File.readText(path) ?: throw IllegalArgumentException("File not found: $path")
            serializer.readJson(MarykYamlReader(content), null)
        }
        SaveFormat.JSON -> {
            val content = File.readText(path) ?: throw IllegalArgumentException("File not found: $path")
            val iterator = content.iterator()
            val reader = JsonReader { if (iterator.hasNext()) iterator.nextChar() else Char.MIN_VALUE }
            serializer.readJson(reader, null)
        }
        SaveFormat.PROTO -> {
            val bytes = File.readBytes(path) ?: throw IllegalArgumentException("File not found: $path")
            var index = 0
            serializer.readProtoBuf(bytes.size, reader = { bytes[index++] }, context = null)
        }
        SaveFormat.KOTLIN -> throw IllegalArgumentException("Kotlin input is not supported.")
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
            val content = File.readText(path) ?: throw IllegalArgumentException("File not found: $path")
            ValuesWithMetaData.Serializer.readJson(MarykYamlReader(content), requestContext)
        }
        SaveFormat.JSON -> {
            val content = File.readText(path) ?: throw IllegalArgumentException("File not found: $path")
            val iterator = content.iterator()
            val reader = JsonReader { if (iterator.hasNext()) iterator.nextChar() else Char.MIN_VALUE }
            ValuesWithMetaData.Serializer.readJson(reader, requestContext)
        }
        SaveFormat.PROTO -> {
            val bytes = File.readBytes(path) ?: throw IllegalArgumentException("File not found: $path")
            var index = 0
            ValuesWithMetaData.Serializer.readProtoBuf(bytes.size, reader = { bytes[index++] }, context = requestContext)
        }
        SaveFormat.KOTLIN -> throw IllegalArgumentException("Kotlin input is not supported.")
    }

    val meta = ValuesWithMetaData(metaValues)
    return LoadedRecordValues(meta.values, meta)
}
