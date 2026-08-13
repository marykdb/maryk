package io.maryk.app.data

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.contextual.IsDataModelReference
import maryk.generator.kotlin.GenerationContext
import maryk.generator.kotlin.generateKotlin
import maryk.generator.proto3.generateProto3FileHeader
import maryk.generator.proto3.generateProto3Schema
import maryk.file.File
import maryk.file.writeTextViaTemporaryFile
import maryk.json.JsonWriter
import maryk.yaml.YamlWriter

enum class ModelExportFormat(
    val label: String,
    val extension: String,
) {
    JSON("JSON", "json"),
    YAML("YAML", "yaml"),
    PROTO("Proto", "proto"),
    KOTLIN("Kotlin", "kt"),
}

private const val defaultExportPackage = "maryk.exported"

internal inline fun <T> preflightAndPublish(
    values: Iterable<T>,
    render: (T) -> String,
    publish: (T, String) -> Unit,
) {
    val rendered = values.map { value -> value to render(value) }
    rendered.forEach { (value, content) -> publish(value, content) }
}

internal fun exportModelToFolder(
    model: IsRootDataModel,
    format: ModelExportFormat,
    folder: String,
    allModels: Map<String, IsRootDataModel>,
) {
    val content = serializeModel(model, format, allModels)
    writeSerializedModelToFolder(model, format, folder, content)
}

internal fun writeSerializedModelToFolder(
    model: IsRootDataModel,
    format: ModelExportFormat,
    folder: String,
    content: String,
) {
    val fileName = "${sanitizeFilePart(model.Meta.name)}.${format.extension}"
    val path = joinExportPath(folder, fileName)
    File.writeTextViaTemporaryFile(path, content)
}

internal fun modelExportFileNames(
    modelNames: Iterable<String>,
    format: ModelExportFormat,
): Map<String, String> = portableFileNames(modelNames) { modelName ->
    "${sanitizeFilePart(modelName)}.${format.extension}"
}

internal fun serializeModel(
    model: IsRootDataModel,
    format: ModelExportFormat,
    allModels: Map<String, IsRootDataModel>,
): String {
    return when (format) {
        ModelExportFormat.JSON -> serializeModelAsJson(model, allModels)
        ModelExportFormat.YAML -> serializeModelAsYaml(model, allModels)
        ModelExportFormat.PROTO -> serializeModelAsProto(model)
        ModelExportFormat.KOTLIN -> serializeModelAsKotlin(model)
    }
}

private fun serializeModelAsJson(
    model: IsRootDataModel,
    allModels: Map<String, IsRootDataModel>,
): String {
    val rootModel = model as RootDataModel<*>
    val context = buildDefinitionsContext(allModels)
    return buildString {
        val writer = JsonWriter(pretty = true) { append(it) }
        RootDataModel.Model.Serializer.writeObjectAsJson(rootModel, writer, context)
    }
}

private fun serializeModelAsYaml(
    model: IsRootDataModel,
    allModels: Map<String, IsRootDataModel>,
): String {
    val rootModel = model as RootDataModel<*>
    val context = buildDefinitionsContext(allModels)
    return buildString {
        val writer = YamlWriter { append(it) }
        RootDataModel.Model.Serializer.writeObjectAsJson(rootModel, writer, context)
    }
}

private fun serializeModelAsProto(
    model: IsRootDataModel,
): String {
    val generationContext = GenerationContext()
    return buildString {
        generateProto3FileHeader(defaultExportPackage) { append(it) }
        model.generateProto3Schema(generationContext) { append(it) }
    }
}

private fun serializeModelAsKotlin(
    model: IsRootDataModel,
): String {
    val generationContext = GenerationContext()
    return buildString {
        model.generateKotlin(defaultExportPackage, generationContext) { append(it) }
    }
}

private fun buildDefinitionsContext(
    allModels: Map<String, IsRootDataModel>,
): DefinitionsConversionContext {
    val map = mutableMapOf<String, IsDataModelReference<*>>()
    allModels.forEach { (name, dataModel) ->
        map[name] = DataModelReference(dataModel)
    }
    return DefinitionsConversionContext(DefinitionsContext(dataModels = map))
}
