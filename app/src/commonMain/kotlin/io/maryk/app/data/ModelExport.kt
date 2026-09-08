package io.maryk.app.data

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.contextual.IsDataModelReference
import maryk.core.properties.enum.IsIndexedEnumDefinition
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
private const val sharedProtoDefinitionsFileStem = "maryk_shared_definitions"

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
    generationContext: GenerationContext? = null,
): String {
    return when (format) {
        ModelExportFormat.JSON -> serializeModelAsJson(model, allModels)
        ModelExportFormat.YAML -> serializeModelAsYaml(model, allModels)
        ModelExportFormat.PROTO -> serializeModelAsProto(model, generationContext)
        ModelExportFormat.KOTLIN -> serializeModelAsKotlin(model, generationContext)
    }
}

internal fun serializeModels(
    models: Iterable<IsRootDataModel>,
    format: ModelExportFormat,
    allModels: Map<String, IsRootDataModel>,
): List<Pair<IsRootDataModel, String>> {
    val generationContext = when (format) {
        ModelExportFormat.KOTLIN -> GenerationContext()
        else -> null
    }
    return models.map { model ->
        model to serializeModel(model, format, allModels, generationContext)
    }
}

internal fun serializeProtoModels(
    models: Iterable<IsRootDataModel>,
): List<Pair<String, String>> {
    val modelsList = models.toList()
    val modelFileNames = modelExportFileNames(modelsList.map { it.Meta.name }, ModelExportFormat.PROTO)
    val sharedEnums = modelsList.sharedProtoEnums()
    val sharedFileName = sharedEnums.takeIf { it.isNotEmpty() }
        ?.let { sharedProtoDefinitionsFileName(modelFileNames.values) }
    val generationContext = GenerationContext(sharedEnums.toMutableList())
    val modelOutputs = modelsList.map { model ->
        modelFileNames.getValue(model.Meta.name) to serializeModelAsProto(
            model,
            generationContext,
            sharedFileName?.removeSuffix(".proto")?.let(::listOf).orEmpty(),
        )
    }
    return if (sharedFileName == null) {
        modelOutputs
    } else {
        listOf(sharedFileName to serializeSharedProtoEnums(sharedEnums)) + modelOutputs
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
    generationContext: GenerationContext?,
    protosToImport: List<String> = emptyList(),
): String {
    return buildString {
        generateProto3FileHeader(defaultExportPackage, protosToImport) { append(it) }
        model.generateProto3Schema(generationContext ?: GenerationContext()) { append(it) }
    }
}

private fun List<IsRootDataModel>.sharedProtoEnums(): List<IsIndexedEnumDefinition<*>> {
    val usages = mutableListOf<Pair<IsIndexedEnumDefinition<*>, Int>>()
    for (model in this) {
        val modelEnums = mutableListOf<IsIndexedEnumDefinition<*>>()
        for (property in model) {
            val definition = property.definition as? IsSerializablePropertyDefinition<*, *> ?: continue
            definition.collectProtoEnums(modelEnums)
        }
        for (enum in modelEnums) {
            val previousIndex = usages.indexOfFirst { (other) -> other.name == enum.name && other == enum }
            if (previousIndex < 0) usages += enum to 1
            else usages[previousIndex] = enum to (usages[previousIndex].second + 1)
        }
    }
    val shared = usages.filter { it.second > 1 }.map { it.first }
        .filter { enum ->
            // GenerationContext uses enum equality, which ignores the name for
            // declared cases. Such enums must stay local to avoid suppressing
            // a different type's declaration in another generated file.
            usages.none { (other) ->
                (other.name == enum.name && other != enum) || (other.name != enum.name && other == enum)
            }
        }
    // Proto enum cases share their enclosing scope with their enum type. Keep
    // colliding definitions inside each model instead of introducing invalid
    // top-level symbols when unrelated models use the same case names.
    val symbols = shared.associate { enum ->
        enum.name to (listOf(enum.name, "UNKNOWN_${enum.name.uppercase()}") + enum.cases().map { it.name })
    }
    val symbolCounts = (symbols.values.flatten() + map { it.Meta.name }).groupingBy { it }.eachCount()
    return shared.filter { enum -> symbols.getValue(enum.name).all { symbolCounts.getValue(it) == 1 } }
}

private fun IsSerializablePropertyDefinition<*, *>.collectProtoEnums(
    output: MutableList<IsIndexedEnumDefinition<*>>,
) {
    when (this) {
        is EnumDefinition<*> -> {
            if (output.none { it.name == enum.name && it == enum }) output += enum
        }
        is IsCollectionDefinition<*, *, *, *> -> valueDefinition.collectProtoEnums(output)
        is IsMapDefinition<*, *, *> -> {
            keyDefinition.collectProtoEnums(output)
            valueDefinition.collectProtoEnums(output)
        }
        is MultiTypeDefinition<*, *> -> {
            for (type in typeEnum.cases()) {
                type.definition?.collectProtoEnums(output)
            }
        }
    }
}

private fun sharedProtoDefinitionsFileName(modelFileNames: Collection<String>): String {
    var stem = sharedProtoDefinitionsFileStem
    while (modelFileNames.any { it.equals("$stem.proto", ignoreCase = true) }) {
        stem += '_'
    }
    return "$stem.proto"
}

private fun serializeSharedProtoEnums(enums: List<IsIndexedEnumDefinition<*>>): String = buildString {
    generateProto3FileHeader(defaultExportPackage) { append(it) }
    enums.forEachIndexed { index, enum ->
        if (index > 0) append("\n\n")
        enum.generateProto3Schema(::append)
    }
}

private fun serializeModelAsKotlin(
    model: IsRootDataModel,
    generationContext: GenerationContext?,
): String {
    return buildString {
        model.generateKotlin(defaultExportPackage, generationContext ?: GenerationContext()) { append(it) }
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
