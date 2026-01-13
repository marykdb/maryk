package io.maryk.app

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.properties.graph.GraphContext
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.filters.IsFilter
import maryk.core.query.orders.IsOrder
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.requests.ScanRequest
import maryk.core.values.Values
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.yaml.YamlWriter
import maryk.core.yaml.MarykYamlReader

internal data class DisplayField(
    val path: String,
    val reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
)

internal object ScanQueryParser {
    fun parseFilter(dataModel: IsRootDataModel, raw: String): IsFilter {
        val fieldLine = formatYamlField("where", raw)
        val request = parseScanRequest(dataModel, listOf(fieldLine))
        return request.where ?: throw IllegalArgumentException("No filter parsed.")
    }

    fun parseSelectGraph(
        dataModel: IsRootDataModel,
        paths: List<String>,
    ): RootPropRefGraph<IsRootDataModel>? {
        if (paths.isEmpty()) return null
        val yaml = buildSelectGraphYaml(paths)
        val reader = MarykYamlReader(yaml)
        val context = GraphContext(dataModel)
        val values = RootPropRefGraph.Serializer.readJson(reader, context)
        @Suppress("UNCHECKED_CAST")
        return values.toDataObject() as RootPropRefGraph<IsRootDataModel>
    }

    fun parseOrder(dataModel: IsRootDataModel, tokens: List<String>): IsOrder? {
        val parts = parseReferencePaths(tokens)
        if (parts.isEmpty()) return null
        val context = createRequestContext(dataModel)
        val orders = parts.map { token ->
            val trimmed = token.trim()
            val (path, descending) = parseOrderToken(trimmed)
            val reference = dataModel.getPropertyReferenceByName(path, context)
            if (descending) reference.descending() else reference.ascending()
        }

        return if (orders.size == 1) {
            orders.first()
        } else {
            Orders(*orders.toTypedArray())
        }
    }

    fun parseReferencePaths(tokens: List<String>): List<String> =
        tokens.flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun parseOrderToken(token: String): Pair<String, Boolean> {
        val trimmed = token.trim()
        return when {
            trimmed.startsWith("-") -> trimmed.drop(1).trim() to true
            trimmed.startsWith("+") -> trimmed.drop(1).trim() to false
            trimmed.endsWith(":desc", ignoreCase = true) ->
                trimmed.dropLast(5).trim() to true
            trimmed.endsWith(":asc", ignoreCase = true) ->
                trimmed.dropLast(4).trim() to false
            else -> trimmed to false
        }
    }

    private fun formatYamlField(name: String, raw: String): String {
        val trimmed = raw.trim()
        val needsBlock = trimmed.startsWith("-")
            || trimmed.contains("\n")
            || (trimmed.contains(":") && !trimmed.startsWith("{") && !trimmed.startsWith("["))
        return if (needsBlock) {
            val indented = trimmed.lineSequence().joinToString("\n") { "  $it" }
            "$name:\n$indented"
        } else {
            "$name: $trimmed"
        }
    }

    private fun parseScanRequest(
        dataModel: IsRootDataModel,
        fieldLines: List<String>,
    ): ScanRequest<IsRootDataModel> {
        val yaml = buildString {
            append("from: ${dataModel.Meta.name}\n")
            fieldLines.forEach { line ->
                append(line.trimEnd())
                append('\n')
            }
        }
        val context = createRequestContext(dataModel)
        val reader = MarykYamlReader(yaml)
        val values = ScanRequest.Serializer.readJson(reader, context)
        return ScanRequest.invoke(values)
    }

    fun createRequestContext(dataModel: IsRootDataModel): RequestContext {
        val definitionsContext = DefinitionsContext(
            mutableMapOf(dataModel.Meta.name to DataModelReference(dataModel))
        )
        return RequestContext(definitionsContext, dataModel = dataModel)
    }

    private fun buildSelectGraphYaml(paths: List<String>): String {
        val root = SelectNode()
        paths.forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@forEach
            val segments = trimmed.split('.').filter { it.isNotBlank() }
            root.addPath(segments)
        }
        return root.toYamlLines(indent = 0).joinToString(separator = "\n")
    }

    private class SelectNode {
        private val children: LinkedHashMap<String, SelectNode> = linkedMapOf()

        fun addPath(segments: List<String>, index: Int = 0) {
            if (index >= segments.size) {
                return
            }
            val key = segments[index]
            val child = children.getOrPut(key) { SelectNode() }
            child.addPath(segments, index + 1)
        }

        fun toYamlLines(indent: Int): List<String> {
            val lines = mutableListOf<String>()
            children.forEach { (name, child) ->
                val prefix = " ".repeat(indent)
                if (child.children.isEmpty()) {
                    lines.add("$prefix- $name")
                } else {
                    lines.add("$prefix- $name:")
                    lines.addAll(child.toYamlLines(indent + 2))
                }
            }
            return lines
        }
    }
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
        is IsSerializablePropertyDefinition<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            definition as IsSerializablePropertyDefinition<Any, IsPropertyContext>
        }
        is IsValueDefinitionWrapper<*, *, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            definition.definition as? IsSerializablePropertyDefinition<Any, IsPropertyContext>
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
