package io.maryk.cli

import com.varabyte.kotter.foundation.input.InputCompleter
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys
import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key as MarykKey
import maryk.core.query.RequestContext
import maryk.core.query.filters.And
import maryk.core.query.filters.IsFilter
import maryk.core.query.orders.IsOrder
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.yaml.YamlWriter
import kotlin.math.max
import kotlin.math.min

class ScanViewerInteraction(
    private val dataModel: IsRootDataModel,
    private val dataStore: IsDataStore,
    private val requestContext: RequestContext,
    private val startKey: MarykKey<IsRootDataModel>?,
    private val includeStart: Boolean,
    private val toVersion: ULong?,
    private val filterSoftDeleted: Boolean,
    where: IsFilter?,
    private val order: IsOrder?,
    private var selectPaths: List<String>,
    private var displayPaths: List<String>,
    private var selectGraph: RootPropRefGraph<IsRootDataModel>?,
    private val pageSize: UInt,
    private val maxLineChars: Int,
    private val terminalHeight: Int,
) : CliInteraction {
    override val promptLabel: String = "scan> "
    override val introLines: List<String> = listOf(
        "Scanning ${dataModel.Meta.name}. Use Up/Down to move, PgUp/PgDn for pages, q/quit/exit to close.",
        "Commands: get | save <dir> [--yaml|--json|--proto] [--meta] | load <file> [--yaml|--json|--proto] [--if-version <n>] [--meta] | delete [--hard] | filter <expr> | show <refs>",
    )

    private var currentWhere: IsFilter? = where

    private var rows: MutableList<ScanRow> = mutableListOf()
    private var selectedIndex = 0
    private var offset = 0
    private var nextStartKey: MarykKey<IsRootDataModel>? = startKey
    private var nextIncludeStart = includeStart
    private var endReached = false
    private var statusMessage: String? = null
    private var statusInPrompt = false
    private var pendingDelete = false
    private var pendingHardDelete = false
    private var displayFields: List<DisplayField> = emptyList()
    private val referencePaths by lazy { collectReferencePaths(dataModel) }

    private val completer: InputCompleter = object : InputCompleter {
        override fun complete(input: String): String? {
            val trimmed = input.trimStart()
            if (trimmed.isEmpty()) return null
            val tokens = trimmed.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
            val endsWithSpace = input.lastOrNull()?.isWhitespace() == true
            val currentToken = if (endsWithSpace) "" else tokens.lastOrNull().orEmpty()

            if (pendingDelete) {
                return completeToken(currentToken, YES_NO_OPTIONS)
            }

            val commands = listOf("get", "save", "load", "delete", "filter", "show", "q", "quit", "exit")
            if (tokens.size == 1 && !endsWithSpace) {
                return completeToken(currentToken, commands)
            }

            val command = tokens.first().lowercase()
            when (command) {
                "save" -> {
                    if (tokens.size == 1 && !endsWithSpace) {
                        return completeToken(currentToken, listOf("save"))
                    }
                    if (currentToken.startsWith("--")) {
                        return completeToken(currentToken, listOf("--yaml", "--json", "--proto", "--meta"))
                    }
                    if (endsWithSpace) {
                        return "--yaml"
                    }
                }
                "load" -> {
                    if (tokens.size == 1 && !endsWithSpace) {
                        return completeToken(currentToken, listOf("load"))
                    }
                    if (currentToken.startsWith("--")) {
                        return completeToken(currentToken, listOf("--yaml", "--json", "--proto", "--if-version", "--meta"))
                    }
                    if (endsWithSpace && tokens.size > 1 && tokens.drop(1).any { !it.startsWith("--") }) {
                        return "--yaml"
                    }
                }
                "delete" -> {
                    if (tokens.size == 1 && !endsWithSpace) {
                        return completeToken(currentToken, listOf("delete"))
                    }
                    if (currentToken.startsWith("--")) {
                        return completeToken(currentToken, listOf("--hard"))
                    }
                    if (endsWithSpace) {
                        return "--hard"
                    }
                }
                "show" -> {
                    if (tokens.size == 1 && !endsWithSpace) {
                        return completeToken(currentToken, listOf("show"))
                    }
                    if (tokens.size == 1 && endsWithSpace) {
                        return completeReferenceListToken("", referencePaths)
                    }
                    if (tokens.size >= 2) {
                        return completeReferenceListToken(currentToken, referencePaths)
                    }
                }
            }
            return null
        }
    }

    init {
        resolveDisplayFields()
        loadInitial()
    }

    override fun inputCompleter(): InputCompleter = completer

    override fun promptLines(): List<String> {
        val headerLines = buildHeaderLines()
        val viewHeight = max(1, terminalHeight - FOOTER_AND_PROMPT_LINES - headerLines.size)
        val maxOffset = max(0, rows.size - viewHeight)
        offset = min(offset, maxOffset)

        val lines = buildList {
            addAll(headerLines)
            if (rows.isEmpty()) {
                add("<no results>")
            } else {
                val end = min(rows.size, offset + viewHeight)
                for (index in offset until end) {
                    val row = rows[index]
                    val isSelected = index == selectedIndex
                    add(formatRow(row, isSelected))
                }
                add(footerLine(offset + 1, min(rows.size, offset + viewHeight), rows.size))
            }
            if (statusInPrompt) {
                statusMessage?.let { add(it) }
                statusInPrompt = false
            }
        }
        return lines
    }

    override fun onInput(input: String): InteractionResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            statusMessage = null
            return InteractionResult.Stay()
        }

        if (pendingDelete) {
            return handleDeleteConfirmation(trimmed)
        }

        val args = CommandLineParser.parse(trimmed)
        val tokens = when (args) {
            is CommandLineParser.ParseResult.Success -> args.tokens
            is CommandLineParser.ParseResult.Error -> {
                statusMessage = "Command parse error: ${args.message}"
                return InteractionResult.Stay(lines = statusLines())
            }
        }

        if (tokens.isEmpty()) return InteractionResult.Stay()
        val command = tokens.first().lowercase()
        val arguments = tokens.drop(1)

        return when (command) {
            "q", "quit", "exit" -> InteractionResult.Complete(lines = emptyList())
            "get" -> handleGet()
            "save" -> handleSave(arguments)
            "load" -> handleLoad(arguments)
            "delete" -> handleDelete(arguments)
            "filter" -> handleFilter(arguments)
            "show" -> handleShow(arguments)
            else -> {
                statusMessage = "Unknown command: $command"
                InteractionResult.Stay(lines = statusLines())
            }
        }
    }

    override fun onKeyPressed(key: Key): InteractionKeyResult? {
        if (rows.isEmpty()) return null
        val viewHeight = max(1, terminalHeight - FOOTER_AND_PROMPT_LINES - buildHeaderLines().size)
        val previousIndex = selectedIndex
        when (key) {
            Keys.UP -> moveSelection(-1, viewHeight)
            Keys.DOWN -> moveSelection(1, viewHeight)
            Keys.PAGE_UP -> moveSelection(-viewHeight, viewHeight)
            Keys.PAGE_DOWN -> moveSelection(viewHeight, viewHeight)
            Keys.HOME -> {
                selectedIndex = 0
                offset = 0
            }
            Keys.END -> {
                selectedIndex = rows.size - 1
                offset = max(0, rows.size - viewHeight)
            }
            else -> return null
        }

        if (selectedIndex != previousIndex) {
            return InteractionKeyResult.Rerender
        }
        return InteractionKeyResult.Rerender
    }

    private fun moveSelection(delta: Int, viewHeight: Int) {
        val target = (selectedIndex + delta).coerceIn(0, max(0, rows.size - 1))
        selectedIndex = target
        if (selectedIndex < offset) {
            offset = selectedIndex
        } else if (selectedIndex >= offset + viewHeight) {
            offset = selectedIndex - viewHeight + 1
        }

        val threshold = max(1, viewHeight / 3)
        if (!endReached && selectedIndex >= rows.size - threshold) {
            if (loadNextPage()) {
                offset = min(offset, max(0, rows.size - viewHeight))
            }
        }
    }

    private fun handleGet(): InteractionResult {
        val row = currentRow() ?: return InteractionResult.Stay(lines = listOf("No row selected."))
        val label = "${dataModel.Meta.name} ${row.key}"
        val loadContext = LoadContext(
            label = label,
            dataModel = dataModel,
            key = row.key,
            dataStore = dataStore,
        )
        val refresh = loadContext.refreshView()
        return when (refresh) {
            is RefreshResult.Error -> {
                statusMessage = "Get failed: ${refresh.message}"
                InteractionResult.Stay(lines = statusLines())
            }
            is RefreshResult.Success -> {
                val deleteContext = DeleteContext(label) { hardDelete ->
                    val request = dataModel.delete(row.key, hardDelete = hardDelete)
                    runBlocking { dataStore.execute(request) }
                    if (hardDelete) {
                        listOf("Hard deleted $label.")
                    } else {
                        listOf("Deleted $label.")
                    }
                }
                val viewer = OutputViewerInteraction(
                    lines = refresh.lines,
                    terminalHeight = terminalHeight,
                    saveContext = refresh.saveContext,
                    deleteContext = deleteContext,
                    loadContext = loadContext,
                    returnInteraction = this,
                )
                InteractionResult.Continue(viewer, showIntro = true)
            }
        }
    }

    private fun handleSave(arguments: List<String>): InteractionResult {
        val row = currentRow() ?: return InteractionResult.Stay(lines = listOf("No row selected."))
        val options = parseSaveOptions(arguments)
        if (options is SaveOptionsResult.Error) {
            statusMessage = options.message
            return InteractionResult.Stay(lines = statusLines())
        }
        val saveOptions = (options as SaveOptionsResult.Success).options

        val loadContext = LoadContext(
            label = "${dataModel.Meta.name} ${row.key}",
            dataModel = dataModel,
            key = row.key,
            dataStore = dataStore,
        )
        val refresh = loadContext.refreshView()
        return when (refresh) {
            is RefreshResult.Error -> {
                statusMessage = "Save failed: ${refresh.message}"
                InteractionResult.Stay(lines = statusLines())
            }
            is RefreshResult.Success -> {
                val message = try {
                    refresh.saveContext.save(
                        directory = saveOptions.directory,
                        format = saveOptions.format,
                        includeMeta = saveOptions.includeMeta,
                        packageName = saveOptions.packageName,
                        noDeps = saveOptions.noDeps,
                    )
                } catch (e: Throwable) {
                    "Save failed: ${e.message ?: e::class.simpleName}"
                }
                statusMessage = message
                InteractionResult.Stay(lines = statusLines())
            }
        }
    }

    private fun handleLoad(arguments: List<String>): InteractionResult {
        val row = currentRow() ?: return InteractionResult.Stay(lines = listOf("No row selected."))
        val options = parseLoadOptions(arguments)
        if (options is LoadOptionsResult.Error) {
            statusMessage = options.message
            return InteractionResult.Stay(lines = statusLines())
        }
        val loadOptions = (options as LoadOptionsResult.Success).options

        val loadContext = LoadContext(
            label = "${dataModel.Meta.name} ${row.key}",
            dataModel = dataModel,
            key = row.key,
            dataStore = dataStore,
        )
        val result = try {
            loadContext.loadResult(
                path = loadOptions.path,
                format = loadOptions.format,
                ifVersion = loadOptions.ifVersion,
                useMeta = loadOptions.useMeta,
            )
        } catch (e: Throwable) {
            ApplyResult("Load failed: ${e.message ?: e::class.simpleName}", success = false)
        }
        if (result.success) {
            reloadFromStart()
        }
        statusMessage = result.message
        return InteractionResult.Stay(lines = statusLines())
    }

    private fun handleDelete(arguments: List<String>): InteractionResult {
        val options = parseDeleteOptions(arguments)
        if (options is DeleteOptionsResult.Error) {
            statusMessage = options.message
            return InteractionResult.Stay(lines = statusLines())
        }
        val resolved = (options as DeleteOptionsResult.Success).options
        pendingDelete = true
        pendingHardDelete = resolved.hardDelete
        statusMessage = if (pendingHardDelete) {
            "Delete ${currentRowLabel()} (hard)? Type yes or no."
        } else {
            "Delete ${currentRowLabel()}? Type yes or no."
        }
        return InteractionResult.Stay(lines = statusLines())
    }

    private fun handleFilter(arguments: List<String>): InteractionResult {
        if (arguments.isEmpty()) {
            statusMessage = "Filter requires an expression."
            return InteractionResult.Stay(lines = statusLines())
        }
        val raw = arguments.joinToString(" ")
        val filter = try {
            ScanQueryParser.parseFilter(dataModel, raw)
        } catch (e: Throwable) {
            statusMessage = "Filter failed: ${e.message ?: e::class.simpleName}"
            return InteractionResult.Stay(lines = statusLines())
        }

        currentWhere = when (val existing = currentWhere) {
            null -> filter
            is And -> And(existing.filters + filter)
            else -> And(existing, filter)
        }
        reloadFromStart()
        statusMessage = "Filter added."
        return InteractionResult.Stay(lines = statusLines())
    }

    private fun handleShow(arguments: List<String>): InteractionResult {
        if (arguments.isEmpty()) {
            statusMessage = "Show requires one or more references."
            return InteractionResult.Stay(lines = statusLines())
        }
        displayPaths = ScanQueryParser.parseReferencePaths(arguments)
        resolveDisplayFields()
        updateSelectGraph()
        reloadFromStart()
        statusMessage = "Display fields updated."
        return InteractionResult.Stay(lines = statusLines())
    }

    private fun loadInitial() {
        rows = mutableListOf()
        endReached = false
        nextStartKey = startKey
        nextIncludeStart = includeStart
        selectedIndex = 0
        offset = 0
        loadNextPage()
    }

    private fun reloadFromStart() {
        loadInitial()
        statusInPrompt = true
    }

    private fun loadNextPage(): Boolean {
        if (endReached) return false
        val limit = pageSize
        val request = dataModel.scan(
            startKey = nextStartKey,
            select = selectGraph,
            where = currentWhere,
            order = order,
            limit = limit,
            includeStart = nextIncludeStart,
            toVersion = toVersion,
            filterSoftDeleted = filterSoftDeleted,
            allowTableScan = true,
        )
        val response = runBlocking { dataStore.execute(request) }
        if (response.values.isEmpty()) {
            endReached = true
            return false
        }

        response.values.forEach { valuesWithMeta ->
            rows.add(
                ScanRow(
                    key = valuesWithMeta.key,
                    values = valuesWithMeta.values,
                    isDeleted = valuesWithMeta.isDeleted,
                    lastVersion = valuesWithMeta.lastVersion,
                )
            )
        }
        nextStartKey = response.values.last().key
        nextIncludeStart = false

        if (response.values.size < limit.toInt()) {
            endReached = true
        }
        return true
    }

    private fun resolveDisplayFields() {
        if (displayPaths.isEmpty()) {
            displayFields = emptyList()
            return
        }
        displayFields = displayPaths.map { path ->
            val reference = dataModel.getPropertyReferenceByName(path, requestContext)
            DisplayField(path, reference)
        }
    }

    private fun updateSelectGraph() {
        val merged = (selectPaths + displayPaths).distinct()
        selectGraph = try {
            ScanQueryParser.parseSelectGraph(dataModel, merged)
        } catch (_: Throwable) {
            selectGraph
        }
    }

    private fun formatRow(row: ScanRow, selected: Boolean): String {
        val prefix = if (selected) ">" else " "
        val key = row.key.toString()
        val summary = buildSummary(row)
        val base = if (summary.isBlank()) "$prefix $key" else "$prefix $key $summary"
        return truncate(base, maxLineChars)
    }

    private fun buildSummary(row: ScanRow): String {
        val summaryLimit = maxLineChars - (row.key.toString().length + 3)
        if (summaryLimit <= 0) return ""

        if (displayFields.isEmpty()) {
            val serialized = serializeValues(row.values)
            val compact = serialized.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
            return truncate(compact, summaryLimit)
        }

        val builder = StringBuilder()
        for (field in displayFields) {
            @Suppress("UNCHECKED_CAST")
            val reference = field.reference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>
            val value = row.values[reference]
            val segment = "${field.path}=${formatValue(field.reference, value)}"
            val separator = if (builder.isEmpty()) "" else " | "
            if (builder.length + separator.length + segment.length > summaryLimit) {
                if (builder.isEmpty()) {
                    builder.append(truncate(segment, summaryLimit))
                }
                break
            }
            builder.append(separator)
            builder.append(segment)
        }
        return builder.toString()
    }

    private fun formatValue(
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

    private fun serializeValues(values: Values<IsRootDataModel>): String {
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

    private fun buildHeaderLines(): List<String> {
        val lines = mutableListOf<String>()
        val base = buildString {
            append("Scan ${dataModel.Meta.name} (${rows.size}")
            if (endReached) append(", end reached")
            append(")")
        }
        lines.add(base)
        currentWhere?.let { lines.add("Where: $it") }
        order?.let { lines.add("Order: $it") }
        if (displayPaths.isNotEmpty()) {
            lines.add("Show: ${displayPaths.joinToString(", ")}")
        }
        return lines
    }

    private fun currentRow(): ScanRow? = rows.getOrNull(selectedIndex)

    private fun currentRowLabel(): String =
        currentRow()?.let { "${dataModel.Meta.name} ${it.key}" } ?: dataModel.Meta.name

    private fun handleDeleteConfirmation(input: String): InteractionResult {
        val row = currentRow()
            ?: return InteractionResult.Stay(lines = listOf("No row selected."))
        return when (input.lowercase()) {
            "yes", "y" -> {
                pendingDelete = false
                val label = "${dataModel.Meta.name} ${row.key}"
                val message = try {
                    val request = dataModel.delete(row.key, hardDelete = pendingHardDelete)
                    runBlocking { dataStore.execute(request) }
                    if (pendingHardDelete) {
                        "Hard deleted $label."
                    } else {
                        "Deleted $label."
                    }
                } catch (e: Throwable) {
                    "Delete failed: ${e.message ?: e::class.simpleName}"
                }
                pendingHardDelete = false
                reloadFromStart()
                statusMessage = message
                InteractionResult.Stay(lines = statusLines())
            }
            "no", "n", "cancel" -> {
                pendingDelete = false
                pendingHardDelete = false
                InteractionResult.Stay(lines = listOf("Delete cancelled."))
            }
            else -> InteractionResult.Stay(lines = listOf("Type yes or no."))
        }
    }

    private fun statusLines(): List<String> = statusMessage?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()

    private fun truncate(value: String, limit: Int): String {
        if (value.length <= limit) return value
        return if (limit > 3) {
            value.take(limit - 3) + "..."
        } else {
            value.take(limit)
        }
    }

    private fun footerLine(start: Int, end: Int, total: Int): String =
        "Rows $start-$end of $total  (Up/Down PgUp/PgDn Home/End q/quit/exit)"

    private data class ScanRow(
        val key: MarykKey<IsRootDataModel>,
        val values: Values<IsRootDataModel>,
        val isDeleted: Boolean,
        val lastVersion: ULong,
    )

    private data class DisplayField(
        val path: String,
        val reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
    )

    private data class SaveOptions(
        val directory: String,
        val format: SaveFormat,
        val includeMeta: Boolean,
        val packageName: String?,
        val noDeps: Boolean,
    )

    private sealed class SaveOptionsResult {
        data class Success(val options: SaveOptions) : SaveOptionsResult()
        data class Error(val message: String) : SaveOptionsResult()
    }

    private data class LoadOptions(
        val path: String,
        val format: SaveFormat,
        val ifVersion: ULong?,
        val useMeta: Boolean,
    )

    private sealed class LoadOptionsResult {
        data class Success(val options: LoadOptions) : LoadOptionsResult()
        data class Error(val message: String) : LoadOptionsResult()
    }

    private data class DeleteOptions(
        val hardDelete: Boolean,
    )

    private sealed class DeleteOptionsResult {
        data class Success(val options: DeleteOptions) : DeleteOptionsResult()
        data class Error(val message: String) : DeleteOptionsResult()
    }

    private fun parseSaveOptions(tokens: List<String>): SaveOptionsResult {
        var directory: String? = null
        var includeMeta = false
        var format: SaveFormat? = null
        var packageName: String? = null
        var noDeps = false
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            val lowered = token.lowercase()
            when {
                lowered == "--meta" -> includeMeta = true
                lowered == "--yaml" -> format = selectFormat(format, SaveFormat.YAML)
                    ?: return SaveOptionsResult.Error("Choose only one format: --yaml, --json, or --proto")
                lowered == "--json" -> format = selectFormat(format, SaveFormat.JSON)
                    ?: return SaveOptionsResult.Error("Choose only one format: --yaml, --json, or --proto")
                lowered == "--proto" -> format = selectFormat(format, SaveFormat.PROTO)
                    ?: return SaveOptionsResult.Error("Choose only one format: --yaml, --json, or --proto")
                lowered.startsWith("--package=") -> {
                    packageName = token.substringAfter("=").ifBlank {
                        return SaveOptionsResult.Error("`--package` requires a value.")
                    }
                }
                lowered == "--package" -> {
                    packageName = tokens.getOrNull(index + 1)
                        ?: return SaveOptionsResult.Error("`--package` requires a value.")
                    index += 1
                }
                lowered == "--no-deps" -> noDeps = true
                token.startsWith("--") -> return SaveOptionsResult.Error("Unknown option: $token")
                directory == null -> directory = token
                else -> return SaveOptionsResult.Error("Unexpected argument: $token")
            }
            index += 1
        }

        val resolvedDir = directory ?: "./"
        val resolvedFormat = format ?: SaveFormat.YAML
        if (packageName != null) {
            return SaveOptionsResult.Error("`--package` is only valid with Kotlin output.")
        }
        if (noDeps) {
            return SaveOptionsResult.Error("No-deps output not available for this data.")
        }

        return SaveOptionsResult.Success(
            SaveOptions(
                directory = resolvedDir,
                format = resolvedFormat,
                includeMeta = includeMeta,
                packageName = packageName,
                noDeps = noDeps,
            )
        )
    }

    private fun parseLoadOptions(tokens: List<String>): LoadOptionsResult {
        var path: String? = null
        var format: SaveFormat? = null
        var ifVersion: ULong? = null
        var useMeta = false
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            val lowered = token.lowercase()
            when {
                lowered == "--yaml" -> format = selectFormat(format, SaveFormat.YAML)
                    ?: return LoadOptionsResult.Error("Choose only one format: --yaml, --json, or --proto")
                lowered == "--json" -> format = selectFormat(format, SaveFormat.JSON)
                    ?: return LoadOptionsResult.Error("Choose only one format: --yaml, --json, or --proto")
                lowered == "--proto" -> format = selectFormat(format, SaveFormat.PROTO)
                    ?: return LoadOptionsResult.Error("Choose only one format: --yaml, --json, or --proto")
                lowered == "--meta" -> useMeta = true
                lowered.startsWith("--if-version=") -> {
                    val value = token.substringAfter("=").ifBlank {
                        return LoadOptionsResult.Error("`--if-version` requires a value.")
                    }
                    ifVersion = value.toULongOrNull()
                        ?: return LoadOptionsResult.Error("Invalid `--if-version` value: $value")
                }
                lowered == "--if-version" -> {
                    val value = tokens.getOrNull(index + 1)
                        ?: return LoadOptionsResult.Error("`--if-version` requires a value.")
                    ifVersion = value.toULongOrNull()
                        ?: return LoadOptionsResult.Error("Invalid `--if-version` value: $value")
                    index += 1
                }
                token.startsWith("--") -> return LoadOptionsResult.Error("Unknown option: $token")
                path == null -> path = token
                else -> return LoadOptionsResult.Error("Unexpected argument: $token")
            }
            index += 1
        }

        val resolvedPath = path ?: return LoadOptionsResult.Error("Load requires a file path.")
        val resolvedFormat = format ?: SaveFormat.YAML
        return LoadOptionsResult.Success(
            LoadOptions(
                path = resolvedPath,
                format = resolvedFormat,
                ifVersion = ifVersion,
                useMeta = useMeta,
            )
        )
    }

    private fun parseDeleteOptions(tokens: List<String>): DeleteOptionsResult {
        var hardDelete = false
        tokens.forEach { token ->
            when (token.lowercase()) {
                "--hard" -> hardDelete = true
                else -> return DeleteOptionsResult.Error("Unknown option: $token")
            }
        }
        return DeleteOptionsResult.Success(DeleteOptions(hardDelete = hardDelete))
    }

    private fun selectFormat(current: SaveFormat?, next: SaveFormat): SaveFormat? =
        if (current != null && current != next) null else next

    private companion object {
        private const val FOOTER_AND_PROMPT_LINES = 3
        private val YES_NO_OPTIONS = listOf("yes", "no")
        private val WHITESPACE_REGEX = Regex("\\s+")

        private fun completeToken(current: String, candidates: List<String>): String? {
            val match = candidates.firstOrNull { it.startsWith(current, ignoreCase = true) } ?: return null
            return match.drop(current.length)
        }
    }
}
