package io.maryk.cli

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.graph.GraphContext
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.filters.IsFilter
import maryk.core.query.orders.IsOrder
import maryk.core.query.orders.Orders
import maryk.core.query.orders.descending
import maryk.core.query.orders.ascending
import maryk.core.query.requests.ScanRequest
import maryk.core.yaml.MarykYamlReader

internal object ScanQueryParser {
    fun parseFilter(
        dataModel: IsRootDataModel,
        raw: String,
    ): IsFilter {
        val fieldLine = formatYamlField("where", raw)
        val request = parseScanRequest(dataModel, listOf(fieldLine))
        return request.where ?: throw IllegalArgumentException("No filter parsed.")
    }

    fun parseSelectGraph(
        dataModel: IsRootDataModel,
        paths: List<String>,
    ): RootPropRefGraph<IsRootDataModel>? {
        val normalizedPaths = paths.map { it.trim() }.filter { it.isNotEmpty() }
        if (normalizedPaths.isEmpty()) return null
        val yaml = buildSelectGraphYaml(normalizedPaths)
        val reader = MarykYamlReader(yaml)
        val context = GraphContext(dataModel)
        val values = RootPropRefGraph.Serializer.readJson(reader, context)
        @Suppress("UNCHECKED_CAST")
        return values.toDataObject() as RootPropRefGraph<IsRootDataModel>
    }

    fun parseOrder(
        dataModel: IsRootDataModel,
        tokens: List<String>,
    ): IsOrder? {
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

    private fun createRequestContext(dataModel: IsRootDataModel): RequestContext {
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
            val segments = splitSelectPath(trimmed)
            root.addPath(segments)
        }
        return root.toYamlLines(indent = 0).joinToString(separator = "\n")
    }

    private fun splitSelectPath(path: String): List<String> {
        val segments = mutableListOf<String>()
        val segment = StringBuilder()
        var bracketDepth = 0

        fun addSegment() {
            val value = segment.toString().trim()
            if (value.isEmpty()) {
                throw IllegalArgumentException("Invalid empty segment in select path `$path`.")
            }
            segments.add(value)
            segment.clear()
        }

        path.forEach { char ->
            when (char) {
                '[' -> {
                    bracketDepth += 1
                    segment.append(char)
                }
                ']' -> {
                    if (bracketDepth == 0) {
                        throw IllegalArgumentException("Unmatched closing bracket in select path `$path`.")
                    }
                    bracketDepth -= 1
                    segment.append(char)
                }
                '.' -> {
                    if (bracketDepth == 0) {
                        addSegment()
                    } else {
                        segment.append(char)
                    }
                }
                else -> segment.append(char)
            }
        }

        if (bracketDepth != 0) {
            throw IllegalArgumentException("Unmatched opening bracket in select path `$path`.")
        }
        addSegment()
        return segments
    }

    private fun String.toYamlSingleQuoted() = "'${replace("'", "''")}'"

    private class SelectNode {
        private val children: LinkedHashMap<String, SelectNode> = linkedMapOf()
        private var isLeaf: Boolean = false

        fun addPath(segments: List<String>, index: Int = 0) {
            if (index >= segments.size) {
                isLeaf = true
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
                val safeName = name.toYamlSingleQuoted()
                if (child.children.isEmpty()) {
                    lines.add("$prefix- $safeName")
                } else {
                    lines.add("$prefix- $safeName:")
                    lines.addAll(child.toYamlLines(indent + 2))
                }
            }
            return lines
        }
    }
}
