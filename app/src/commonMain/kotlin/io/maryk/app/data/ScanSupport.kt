package io.maryk.app.data

import maryk.core.query.QueryTextParser
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.filters.IsFilter
import maryk.core.values.Values
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.yaml.YamlWriter

internal data class DisplayField(
    val path: String,
    val reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
)

internal const val KEY_ORDER_TOKEN = QueryTextParser.KEY_ORDER_TOKEN

internal object ScanQueryParser {
    fun parseFilter(dataModel: IsRootDataModel, raw: String) = QueryTextParser.parseFilter(dataModel, raw)
    fun parseSelectGraph(dataModel: IsRootDataModel, paths: List<String>) = QueryTextParser.parseSelectGraph(dataModel, paths)
    fun parseOrder(dataModel: IsRootDataModel, tokens: List<String>) = QueryTextParser.parseOrder(dataModel, tokens)
    fun parseReferencePaths(tokens: List<String>) = QueryTextParser.parseReferencePaths(tokens)
    fun createRequestContext(dataModel: IsRootDataModel) = QueryTextParser.createRequestContext(dataModel)
}

internal fun resolveDisplayFields(
    dataModel: IsRootDataModel,
    paths: List<String>,
    requestContext: RequestContext,
): List<DisplayField> {
    if (paths.isEmpty()) return emptyList()
    return paths.map { path ->
        val reference = dataModel.getPropertyReferenceByName(path, requestContext)
        DisplayField(path, reference)
    }
}

internal fun buildSummary(
    dataModel: IsRootDataModel,
    values: Values<IsRootDataModel>,
    displayFields: List<DisplayField>,
    requestContext: RequestContext,
    maxChars: Int,
): String {
    val summaryLimit = maxChars
    if (summaryLimit <= 0) return ""

    if (displayFields.isEmpty()) {
        val serialized = serializeValues(dataModel, values, requestContext)
        return serialized.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(summaryLimit)
    }

    val builder = StringBuilder()
    for (field in displayFields) {
        @Suppress("UNCHECKED_CAST")
        val reference = field.reference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>
        val value = values[reference]
        val segment = "${field.path}=${formatValue(field.reference, value)}"
        val separator = if (builder.isEmpty()) "" else " | "
        if (builder.length + separator.length + segment.length > summaryLimit) {
            if (builder.isEmpty()) {
                builder.append(segment.take(summaryLimit))
            }
            break
        }
        builder.append(separator)
        builder.append(segment)
    }
    return builder.toString()
}

private fun serializeValues(
    dataModel: IsRootDataModel,
    values: Values<IsRootDataModel>,
    requestContext: RequestContext,
): String {
    @Suppress("UNCHECKED_CAST")
    val serializer = dataModel.Serializer as IsDataModelSerializer<
        Values<IsRootDataModel>,
        IsRootDataModel,
        IsPropertyContext,
    >
    val output = StringBuilder()
    val writer = YamlWriter { output.append(it) }
    serializer.writeJson(values, writer, context = requestContext)
    return output.toString()
}

internal fun formatValue(
    reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
    value: Any?,
): String {
    if (value == null) return "null"
    val definition = reference.propertyDefinition
    val serializable = when (definition) {
        is IsValueDefinitionWrapper<*, *, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            definition.definition as? IsSerializablePropertyDefinition<Any, IsPropertyContext>
        }
        is IsSerializablePropertyDefinition<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            definition as IsSerializablePropertyDefinition<Any, IsPropertyContext>
        }
        else -> null
    }
    val result = if (serializable != null) {
        val output = StringBuilder()
        val writer = YamlWriter { output.append(it) }
        serializable.writeJsonValue(value, writer, null)
        output.toString()
    } else {
        value.toString()
    }
    return result.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
}
