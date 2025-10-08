package maryk.datastore.terminal

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.apple.foundationdb.tuple.Tuple
import com.jakewharton.mosaic.layout.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import maryk.datastore.terminal.driver.StoredModel
import maryk.datastore.terminal.driver.StoreDriver
import maryk.datastore.terminal.driver.StoreType
import maryk.datastore.terminal.driver.createStoreDriver
import maryk.datastore.terminal.driver.ScanRecord
import maryk.datastore.terminal.renderModelDefinition
import maryk.core.models.serializers.IsJsonSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.TypedValue
import maryk.core.values.Values
import java.util.Base64
import maryk.yaml.YamlWriter

private const val MAX_HISTORY_ENTRIES = 50
private const val DEFAULT_DETAIL_PAGE_LINES = 24
private const val HISTORY_WINDOW_SIZE = 5
private const val HISTORY_PAGE_STEP = 5
private const val DEFAULT_SCAN_LIMIT = 100
private const val SUMMARY_FIELD_LIMIT = 4
private const val SUMMARY_VALUE_MAX = 28

enum class ScanDisplayMode {
    List,
    Detail,
}

class ScanRowState(
    val index: Int,
    val keyBytes: ByteArray,
    val keyBase64: String,
    val values: Values<*>,
    val summary: List<Pair<String, String>>,
    val yamlLines: List<String>,
)

class ScanSession(
    val commandLabel: String,
    val model: StoredModel,
    private val driver: StoreDriver,
    val descending: Boolean,
    initialRecords: List<ScanRecord>,
    nextStartAfterKey: ByteArray?,
) {
    val rows = mutableStateListOf<ScanRowState>()
    val selectedIndex = mutableStateOf(if (initialRecords.isEmpty()) -1 else 0)
    val listOffset = mutableStateOf(0)
    val displayMode = mutableStateOf(ScanDisplayMode.List)
    val yamlOffset = mutableStateOf(0)
    val isLoading = mutableStateOf(false)

    var nextStartAfterKey: ByteArray? = nextStartAfterKey
        private set

    init {
        appendRecords(initialRecords)
    }

    fun currentRow(): ScanRowState? = rows.getOrNull(selectedIndex.value.coerceAtLeast(0))

    fun appendRecords(records: List<ScanRecord>) {
        if (records.isEmpty()) return
        val startIndex = rows.size
        records.forEachIndexed { offset, record ->
            rows += record.toRowState(model, startIndex + offset)
        }
        if (selectedIndex.value < 0 && rows.isNotEmpty()) {
            selectedIndex.value = 0
        }
    }

    fun ensureVisible(pageSize: Int) {
        if (rows.isEmpty()) {
            listOffset.value = 0
            return
        }
        val index = selectedIndex.value.coerceIn(0, rows.lastIndex)
        val offset = listOffset.value
        val maxVisible = offset + pageSize - 1
        when {
            index < offset -> listOffset.value = index
            index > maxVisible -> listOffset.value = (index - pageSize + 1).coerceAtLeast(0)
        }
    }

    fun resetYamlOffset() {
        yamlOffset.value = 0
    }

    fun adjustYamlOffset(delta: Int, pageSize: Int) {
        val row = currentRow() ?: return
        val maxOffset = (row.yamlLines.size - pageSize).coerceAtLeast(0)
        val newOffset = (yamlOffset.value + delta).coerceIn(0, maxOffset)
        if (newOffset != yamlOffset.value) {
            yamlOffset.value = newOffset
        }
    }

    suspend fun loadMore(limit: Int): Boolean {
        val startKey = nextStartAfterKey ?: return false
        if (isLoading.value) return false
        isLoading.value = true
        return try {
            val result = driver.scanRecords(
                name = model.name,
                startAfterKey = startKey,
                descending = descending,
                limit = limit,
            )
            appendRecords(result.records)
            nextStartAfterKey = result.nextStartAfterKey
            result.records.isNotEmpty()
        } finally {
            isLoading.value = false
        }
    }
}

private fun ScanRecord.toRowState(model: StoredModel, index: Int): ScanRowState {
    val summary = renderSummary(model, values)
    val yaml = renderYaml(model, values)
    val keyBase64 = Base64.getEncoder().encodeToString(key)
    return ScanRowState(index, key.copyOf(), keyBase64, values, summary, yaml)
}

private fun renderSummary(model: StoredModel, values: Values<*>): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    var count = 0
    for (wrapper in model.definition) {
        if (count >= SUMMARY_FIELD_LIMIT) break
        val raw = values.original(wrapper.index)
        val formatted = ellipsize(formatSummaryValue(raw), SUMMARY_VALUE_MAX)
        result += wrapper.name to formatted
        count++
    }
    return if (result.isEmpty()) listOf("<no fields>" to "") else result
}

private fun renderYaml(model: StoredModel, values: Values<*>): List<String> {
    val builder = StringBuilder()
    val writer = YamlWriter { builder.append(it) }
    @Suppress("UNCHECKED_CAST")
    val serializer = values.dataModel.Serializer as IsJsonSerializer<in Values<*>, IsPropertyContext>
    serializer.writeJson(values, writer, null)
    val text = builder.toString().trimEnd()
    return if (text.isBlank()) {
        listOf("{}")
    } else {
        text.split('\n')
    }
}

private fun formatSummaryValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> value
    is ByteArray -> Base64.getEncoder().encodeToString(value)
    is Values<*> -> value.toString()
    is TypedValue<*, *> -> "${value.type.name}:${formatSummaryValue(value.value)}"
    is List<*> -> value.joinToString(prefix = "[", postfix = "]", limit = 3) { formatSummaryValue(it) }
    is Set<*> -> value.joinToString(prefix = "{", postfix = "}", limit = 3) { formatSummaryValue(it) }
    is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}", limit = 3) { (key, v) ->
        "${formatSummaryValue(key)}=${formatSummaryValue(v)}"
    }
    else -> value.toString()
}

private fun ellipsize(text: String, maxLength: Int): String =
    if (text.length <= maxLength) text else text.take(maxLength - 1).trimEnd() + "…"

enum class PanelStyle {
    Info,
    Success,
    Warning,
    Error,
}

data class BannerMessage(
    val message: String,
    val style: PanelStyle,
)

data class HistoryEntry(
    val id: Int,
    val label: String?,
    val heading: String?,
    val lines: List<String>,
    val style: PanelStyle,
    val summary: String,
    val scanState: ScanSession? = null,
) {
    fun maxLineOffset(pageSize: Int): Int {
        if (lines.isEmpty()) return 0
        val effectivePageSize = pageSize.coerceAtLeast(1)
        return (lines.size - effectivePageSize).coerceAtLeast(0)
    }

    fun visibleLines(offset: Int, pageSize: Int): List<String> {
        if (lines.isEmpty()) return emptyList()
        val effectivePageSize = pageSize.coerceAtLeast(1)
        val maxOffset = maxLineOffset(effectivePageSize)
        val safeOffset = offset.coerceIn(0, maxOffset)
        return lines.drop(safeOffset).take(effectivePageSize)
    }
}

/** A single field in the connection wizard. */
data class StoreField(
    val key: String,
    val label: String,
    val optional: Boolean = false,
    val description: String? = null,
    val defaultValue: String? = null,
)

sealed interface UiMode {
    data class SelectStore(val selectedIndex: Int) : UiMode
    data class ConfigureStore(
        val storeType: StoreType,
        val fields: List<StoreField>,
        val fieldIndex: Int,
        val values: Map<String, String?>,
        val input: String,
    ) : UiMode {
        val currentField: StoreField get() = fields[fieldIndex]
    }
    data class Prompt(val input: String) : UiMode
    data class ModelSelection(val models: List<StoredModel>, val selectedIndex: Int) : UiMode
}

class TerminalState(initialMode: UiMode) {
    val mode = mutableStateOf(initialMode)
    val isConnecting = mutableStateOf(false)
    val connectionDescription = mutableStateOf<String?>(null)
    val shouldExit = mutableStateOf(false)
    val bannerMessage = mutableStateOf<BannerMessage?>(null)
    val history = mutableStateListOf<HistoryEntry>()
    val selectedHistoryIndex = mutableStateOf(0)
    val activeLineOffset = mutableStateOf(0)
    val detailLinesPerPage = mutableStateOf(DEFAULT_DETAIL_PAGE_LINES)

    private val models = mutableStateListOf<StoredModel>()
    private val driver = mutableStateOf<StoreDriver?>(null)
    private var nextEntryId = 1

    fun updateMode(mode: UiMode) {
        this.mode.value = mode
    }

    fun currentModels(): List<StoredModel> = models.toList()

    fun setModels(newModels: List<StoredModel>) {
        models.clear()
        models.addAll(newModels)
    }

    fun driver(): StoreDriver? = driver.value

    fun setDriver(driver: StoreDriver?) {
        this.driver.value = driver
    }

    fun recordHistory(
        label: String?,
        heading: String?,
        lines: List<String>,
        style: PanelStyle,
        summary: String? = null,
        scanState: ScanSession? = null,
    ) {
        val effectiveSummary = summary
            ?: heading
            ?: lines.firstOrNull()
            ?: "(no details)"
        val entry = HistoryEntry(
            id = nextEntryId++,
            label = label,
            heading = heading,
            lines = lines,
            style = style,
            summary = effectiveSummary,
            scanState = scanState,
        )
        history.add(0, entry)
        while (history.size > MAX_HISTORY_ENTRIES) {
            history.removeLast()
        }
        selectedHistoryIndex.value = 0
        activeLineOffset.value = 0
    }

    fun activeHistoryEntry(): HistoryEntry? = history.getOrNull(selectedHistoryIndex.value)

    fun activeScanSession(): ScanSession? = activeHistoryEntry()?.scanState

    fun scrollActiveLines(delta: Int) {
        val entry = activeHistoryEntry() ?: return
        val maxOffset = entry.maxLineOffset(detailLinesPerPage.value)
        val newOffset = (activeLineOffset.value + delta).coerceIn(0, maxOffset)
        if (newOffset != activeLineOffset.value) {
            activeLineOffset.value = newOffset
        }
    }

    fun updateDetailLinesPerPage(lines: Int) {
        val effective = lines.coerceAtLeast(1)
        if (effective == detailLinesPerPage.value) return
        detailLinesPerPage.value = effective
        val entry = activeHistoryEntry() ?: return
        val maxOffset = entry.maxLineOffset(effective)
        if (activeLineOffset.value > maxOffset) {
            activeLineOffset.value = maxOffset
        }
    }

    fun pageHistory(delta: Int) {
        if (history.isEmpty()) return
        val step = if (delta > 0) HISTORY_PAGE_STEP else -HISTORY_PAGE_STEP
        val newIndex = (selectedHistoryIndex.value + step).coerceIn(0, history.lastIndex)
        if (newIndex != selectedHistoryIndex.value) {
            selectedHistoryIndex.value = newIndex
            activeLineOffset.value = 0
        }
    }

    fun currentHistoryWindow(): HistoryWindowState {
        val total = history.size
        if (total == 0) {
            return HistoryWindowState(
                totalCount = 0,
                startIndex = 0,
                entries = emptyList(),
                newerCount = 0,
                olderCount = 0,
            )
        }
        val selectedIndex = selectedHistoryIndex.value.coerceIn(0, total - 1)
        val windowSize = HISTORY_WINDOW_SIZE.coerceAtMost(total)
        val maxStart = (total - windowSize).coerceAtLeast(0)
        val start = when {
            total <= HISTORY_WINDOW_SIZE -> 0
            selectedIndex <= 1 -> 0
            selectedIndex >= total - 2 -> maxStart
            else -> (selectedIndex - 2).coerceIn(0, maxStart)
        }
        val entries = history.drop(start).take(windowSize)
        val newer = start
        val older = total - (start + entries.size)
        return HistoryWindowState(
            totalCount = total,
            startIndex = start,
            entries = entries,
            newerCount = newer,
            olderCount = older,
        )
    }

    fun showBanner(message: String, style: PanelStyle) {
        bannerMessage.value = BannerMessage(message, style)
    }

    fun clearBanner() {
        bannerMessage.value = null
    }

    fun close() {
        driver.value?.close()
        models.clear()
        history.clear()
        bannerMessage.value = null
    }
}

data class HistoryWindowState(
    val totalCount: Int,
    val startIndex: Int,
    val entries: List<HistoryEntry>,
    val newerCount: Int,
    val olderCount: Int,
)

class TerminalController(
    private val scope: CoroutineScope,
    private val state: TerminalState,
    private val onExit: () -> Unit,
) {
    private val storeFields = mapOf(
        StoreType.RocksDb to listOf(
            StoreField(
                key = "path",
                label = "Path to RocksDB database directory",
                description = "Provide the absolute or relative path to the RocksDB data directory.",
            ),
        ),
        StoreType.FoundationDb to listOf(
            StoreField(
                key = "clusterFile",
                label = "Cluster file path (optional)",
                optional = true,
                description = "Leave empty to use the default FDB configuration (env FDB_CLUSTER_FILE).",
            ),
            StoreField(
                key = "tenant",
                label = "Tenant name (optional)",
                optional = true,
                description = "Tenant tuple element, leave empty when not using tenants.",
            ),
            StoreField(
                key = "directory",
                label = "Directory root path",
                description = "Comma or slash separated list (default maryk).",
                defaultValue = "maryk",
            ),
            StoreField(
                key = "apiVersion",
                label = "API version",
                description = "FoundationDB API version (default 730).",
                defaultValue = "730",
            ),
        ),
    )

    private val helpEntries = mapOf(
        "help" to "help [command] - Show commands or detailed help for a command.",
        "list" to "list | l - List all models stored in the connected database.",
        "model" to "model [name] - Display the stored model structure. Without name opens a selector.",
        "scan" to "scan [desc] <model> - Show up to $DEFAULT_SCAN_LIMIT rows for a model. Add 'desc' for descending order.",
        "exit" to "exit - Close the terminal client.",
    )

    fun startWithConfig(config: StoreConnectionConfig) {
        state.updateMode(UiMode.Prompt(input = ""))
        state.clearBanner()
        connect(config)
    }

    fun beginWizard(showHint: Boolean = true) {
        state.updateMode(UiMode.SelectStore(selectedIndex = 0))
        if (showHint) {
            state.showBanner("Select a store configuration to continue.", PanelStyle.Info)
        }
    }

    fun handleKey(event: KeyEvent): Boolean = when (val mode = state.mode.value) {
        is UiMode.SelectStore -> handleSelectStoreKey(mode, event)
        is UiMode.ConfigureStore -> handleConfigureStoreKey(mode, event)
        is UiMode.Prompt -> handlePromptKey(mode, event)
        is UiMode.ModelSelection -> handleModelSelectionKey(mode, event)
    }

    private fun handleSelectStoreKey(mode: UiMode.SelectStore, event: KeyEvent): Boolean {
        return when (event.key) {
            "ArrowUp" -> {
                val newIndex = (mode.selectedIndex - 1).coerceAtLeast(0)
                state.updateMode(mode.copy(selectedIndex = newIndex))
                true
            }
            "ArrowDown" -> {
                val newIndex = (mode.selectedIndex + 1).coerceAtMost(StoreType.entries.lastIndex)
                state.updateMode(mode.copy(selectedIndex = newIndex))
                true
            }
            "Enter" -> {
                val selectedType = StoreType.entries[mode.selectedIndex]
                val fields = storeFields.getValue(selectedType)
                val initialInput = fields.firstOrNull()?.defaultValue.orEmpty()
                state.updateMode(
                    UiMode.ConfigureStore(
                        storeType = selectedType,
                        fields = fields,
                        fieldIndex = 0,
                        values = emptyMap(),
                        input = initialInput,
                    ),
                )
                true
            }
            else -> false
        }
    }

    private fun handleConfigureStoreKey(
        mode: UiMode.ConfigureStore,
        event: KeyEvent,
    ): Boolean {
        return when (event.key) {
            "Backspace" -> {
                if (mode.input.isNotEmpty()) {
                    state.updateMode(mode.copy(input = mode.input.dropLast(1)))
                }
                true
            }
            "Escape" -> {
                state.clearBanner()
                beginWizard()
                true
            }
            "Enter" -> {
                val rawValue = mode.input
                val finalValue: String? = when {
                    rawValue.isNotEmpty() -> rawValue
                    !mode.currentField.defaultValue.isNullOrEmpty() -> mode.currentField.defaultValue
                    mode.currentField.optional -> null
                    else -> {
                        state.showBanner("${mode.currentField.label} is required.", PanelStyle.Error)
                        return true
                    }
                }
                val updatedValues = mode.values.toMutableMap()
                updatedValues[mode.currentField.key] = finalValue

                if (mode.fieldIndex + 1 < mode.fields.size) {
                    val nextField = mode.fields[mode.fieldIndex + 1]
                    state.updateMode(
                        mode.copy(
                            fieldIndex = mode.fieldIndex + 1,
                            values = updatedValues,
                            input = nextField.defaultValue.orEmpty(),
                        ),
                    )
                } else {
                    buildConfig(mode.storeType, updatedValues)?.let { connect(it) }
                }
                true
            }
            "Tab" -> {
                handleConfigureStoreKey(mode, KeyEvent("Enter"))
            }
            else -> {
                val key = event.key
                val character = when {
                    key.length == 1 -> key
                    key == "Space" -> " "
                    else -> null
                }
                if (character != null) {
                    state.updateMode(mode.copy(input = mode.input + character))
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun handlePromptKey(mode: UiMode.Prompt, event: KeyEvent): Boolean {
        return when (event.key) {
            "Backspace" -> {
                if (mode.input.isNotEmpty()) {
                    state.updateMode(mode.copy(input = mode.input.dropLast(1)))
                }
                true
            }
            "ArrowUp" -> {
                if (mode.input.isEmpty()) {
                    val scan = state.activeScanSession()
                    if (scan != null) {
                        val pageSize = state.detailLinesPerPage.value
                        if (scan.displayMode.value == ScanDisplayMode.List) {
                            if (scan.rows.isNotEmpty()) {
                                val newIndex = (scan.selectedIndex.value - 1).coerceAtLeast(0)
                                if (newIndex != scan.selectedIndex.value) {
                                    scan.selectedIndex.value = newIndex
                                    scan.ensureVisible(pageSize)
                                    scan.resetYamlOffset()
                                }
                            }
                        } else {
                            scan.adjustYamlOffset(-1, pageSize)
                        }
                        true
                    } else {
                        state.scrollActiveLines(-1)
                        true
                    }
                } else {
                    false
                }
            }
            "ArrowDown" -> {
                if (mode.input.isEmpty()) {
                    val scan = state.activeScanSession()
                    if (scan != null) {
                        val pageSize = state.detailLinesPerPage.value
                        if (scan.displayMode.value == ScanDisplayMode.List) {
                            if (scan.rows.isNotEmpty()) {
                                val lastIndex = scan.rows.lastIndex
                                if (scan.selectedIndex.value < lastIndex) {
                                    scan.selectedIndex.value = (scan.selectedIndex.value + 1).coerceAtMost(lastIndex)
                                    scan.ensureVisible(pageSize)
                                    scan.resetYamlOffset()
                                } else if (scan.nextStartAfterKey != null && !scan.isLoading.value) {
                                    scope.launch {
                                        if (scan.loadMore(DEFAULT_SCAN_LIMIT)) {
                                            scan.selectedIndex.value = scan.rows.lastIndex
                                            scan.ensureVisible(pageSize)
                                            scan.resetYamlOffset()
                                        }
                                    }
                                }
                            }
                        } else {
                            scan.adjustYamlOffset(1, pageSize)
                        }
                        true
                    } else {
                        state.scrollActiveLines(1)
                        true
                    }
                } else {
                    false
                }
            }
            "PageUp" -> {
                if (mode.input.isEmpty()) {
                    val scan = state.activeScanSession()
                    if (scan != null && scan.displayMode.value == ScanDisplayMode.Detail) {
                        val pageSize = state.detailLinesPerPage.value
                        scan.adjustYamlOffset(-pageSize, pageSize)
                        true
                    } else {
                        state.pageHistory(1)
                        true
                    }
                } else {
                    false
                }
            }
            "PageDown" -> {
                if (mode.input.isEmpty()) {
                    val scan = state.activeScanSession()
                    if (scan != null && scan.displayMode.value == ScanDisplayMode.Detail) {
                        val pageSize = state.detailLinesPerPage.value
                        scan.adjustYamlOffset(pageSize, pageSize)
                        true
                    } else {
                        state.pageHistory(-1)
                        true
                    }
                } else {
                    false
                }
            }
            "ArrowRight" -> {
                if (mode.input.isEmpty()) {
                    val scan = state.activeScanSession()
                    if (scan != null && scan.displayMode.value == ScanDisplayMode.List && scan.rows.isNotEmpty()) {
                        scan.displayMode.value = ScanDisplayMode.Detail
                        scan.resetYamlOffset()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            "ArrowLeft" -> {
                if (mode.input.isEmpty()) {
                    val scan = state.activeScanSession()
                    if (scan != null && scan.displayMode.value == ScanDisplayMode.Detail) {
                        scan.displayMode.value = ScanDisplayMode.List
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            "Enter" -> {
                val command = mode.input.trim()
                state.updateMode(mode.copy(input = ""))
                if (command.isNotEmpty()) {
                    state.clearBanner()
                    executeCommand(command)
                }
                true
            }
            else -> {
                val key = event.key
                val character = when {
                    key.length == 1 -> key
                    key == "Space" -> " "
                    else -> null
                }
                if (character != null) {
                    state.updateMode(mode.copy(input = mode.input + character))
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun handleModelSelectionKey(
        mode: UiMode.ModelSelection,
        event: KeyEvent,
    ): Boolean {
        return when (event.key) {
            "ArrowUp" -> {
                val newIndex = (mode.selectedIndex - 1).coerceAtLeast(0)
                state.updateMode(mode.copy(selectedIndex = newIndex))
                true
            }
            "ArrowDown" -> {
                val newIndex = (mode.selectedIndex + 1).coerceAtMost((mode.models.size - 1).coerceAtLeast(0))
                state.updateMode(mode.copy(selectedIndex = newIndex))
                true
            }
            "Enter" -> {
                mode.models.getOrNull(mode.selectedIndex)?.let { model ->
                    state.clearBanner()
                    displayModel("model ${model.name}", model.name)
                }
                state.updateMode(UiMode.Prompt(input = ""))
                true
            }
            "Escape" -> {
                state.updateMode(UiMode.Prompt(input = ""))
                true
            }
            else -> false
        }
    }

    private fun executeCommand(command: String) {
        val tokens = command.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        val action = tokens.first().lowercase()
        val argument = tokens.drop(1).joinToString(" ")

        if (state.isConnecting.value) {
            state.showBanner("Connection in progress. Please wait...", PanelStyle.Warning)
            return
        }

        when (action) {
            "help", "h" -> showHelp(command, argument)
            "list", "l" -> listModels(command)
            "model" -> {
                if (argument.isNotEmpty()) {
                    displayModel(command, argument)
                } else {
                    openModelSelector()
                }
            }
            "scan" -> scanModel(command, tokens.drop(1))
            "exit" -> exit(command)
            "connect" -> {
                state.showBanner(
                    "Reconnect is not supported. Restart the client to choose another store.",
                    PanelStyle.Warning,
                )
                state.recordHistory(
                    label = command,
                    heading = "Reconnect not supported",
                    lines = listOf("Restart the client to connect to a different store."),
                    style = PanelStyle.Warning,
                )
            }
            else -> {
                val message = "Unknown command '$action'. Type 'help' to see available commands."
                state.showBanner(message, PanelStyle.Error)
                state.recordHistory(
                    label = command,
                    heading = "Unknown command",
                    lines = listOf(message),
                    style = PanelStyle.Error,
                )
            }
        }
    }

    private fun showHelp(commandLabel: String, argument: String) {
        val title: String
        val lines: List<String>
        if (argument.isBlank()) {
            title = "Commands"
            lines = helpEntries.values.map { it }
        } else {
            val key = argument.lowercase()
            val match = helpEntries.entries.firstOrNull { (cmd, _) ->
                cmd == key || cmd.startsWith(key)
            }
            if (match != null) {
                title = "Help: ${match.key}"
                lines = listOf(match.value)
            } else {
                title = "Help"
                lines = listOf("No help available for '$argument'.")
            }
        }
        state.recordHistory(
            label = commandLabel,
            heading = title,
            lines = lines,
            style = PanelStyle.Info,
        )
    }

    private fun scanModel(commandLabel: String, args: List<String>) {
        val driver = state.driver()
        if (driver == null) {
            val message = "No store connected. Complete the wizard first."
            state.showBanner(message, PanelStyle.Warning)
            state.recordHistory(
                label = commandLabel,
                heading = "No connection",
                lines = listOf(message),
                style = PanelStyle.Warning,
            )
            return
        }

        if (args.isEmpty()) {
            val message = "Usage: scan [desc] <model>"
            state.showBanner(message, PanelStyle.Warning)
            state.recordHistory(
                label = commandLabel,
                heading = "Scan usage",
                lines = listOf(message),
                style = PanelStyle.Warning,
            )
            return
        }

        val tokens = args.toMutableList()
        var descending = false
        if (tokens.firstOrNull()?.equals("desc", ignoreCase = true) == true) {
            descending = true
            tokens.removeAt(0)
        }
        if (tokens.isEmpty()) {
            val message = "Missing model name. Usage: scan [desc] <model>"
            state.showBanner(message, PanelStyle.Warning)
            state.recordHistory(
                label = commandLabel,
                heading = "Scan usage",
                lines = listOf(message),
                style = PanelStyle.Warning,
            )
            return
        }
        if (tokens.lastOrNull()?.equals("desc", ignoreCase = true) == true) {
            descending = true
            tokens.removeAt(tokens.lastIndex)
        }
        if (tokens.isEmpty()) {
            val message = "Missing model name. Usage: scan [desc] <model>"
            state.showBanner(message, PanelStyle.Warning)
            state.recordHistory(
                label = commandLabel,
                heading = "Scan usage",
                lines = listOf(message),
                style = PanelStyle.Warning,
            )
            return
        }

        val modelName = tokens.joinToString(" ")
        val orderLabel = if (descending) "descending" else "ascending"
        state.showBanner("Scanning $modelName ($orderLabel)...", PanelStyle.Info)

        scope.launch {
            runCatching {
                val model = driver.loadModel(modelName)
                    ?: throw IllegalStateException("Model '$modelName' not found in store")
                val result = driver.scanRecords(
                    name = modelName,
                    startAfterKey = null,
                    descending = descending,
                    limit = DEFAULT_SCAN_LIMIT,
                )
                model to result
            }.onSuccess { (model, result) ->
                val session = ScanSession(
                    commandLabel = commandLabel,
                    model = model,
                    driver = driver,
                    descending = descending,
                    initialRecords = result.records,
                    nextStartAfterKey = result.nextStartAfterKey,
                )
                val heading = buildString {
                    append("Scan results for $modelName")
                    append(if (descending) " (descending)" else " (ascending)")
                }
                val summary = when {
                    result.records.isEmpty() -> "No rows found for $modelName"
                    result.nextStartAfterKey != null -> "${result.records.size} row(s) listed (partial)"
                    else -> "${result.records.size} row(s) listed"
                }
                val lines = buildList {
                    add("Use ↑/↓ to navigate rows, → for YAML, ← to return to the list.")
                    add("Use PgUp/PgDn to scroll details when viewing YAML.")
                    if (result.nextStartAfterKey != null) {
                        add("Scroll past the end of the list to load more rows.")
                    }
                }
                state.recordHistory(
                    label = commandLabel,
                    heading = heading,
                    lines = lines,
                    style = if (result.records.isEmpty()) PanelStyle.Info else PanelStyle.Success,
                    summary = summary,
                    scanState = session,
                )
                val banner = when {
                    result.records.isEmpty() -> "No rows found for $modelName"
                    result.nextStartAfterKey != null -> "Showing ${result.records.size} rows for $modelName (more available)"
                    else -> "Showing ${result.records.size} rows for $modelName"
                }
                state.showBanner(banner, PanelStyle.Success)
            }.onFailure { error ->
                val message = "Failed to scan $modelName: ${error.message ?: error::class.simpleName}".trim()
                state.recordHistory(
                    label = commandLabel,
                    heading = "Scan failed",
                    lines = listOf(message),
                    style = PanelStyle.Error,
                )
                state.showBanner(message, PanelStyle.Error)
            }
        }
    }

    private fun listModels(commandLabel: String) {
        val driver = state.driver()
        if (driver == null) {
            val message = "No store connected. Complete the wizard first."
            state.showBanner(message, PanelStyle.Warning)
            state.recordHistory(
                label = commandLabel,
                heading = "No connection",
                lines = listOf(message),
                style = PanelStyle.Warning,
            )
            return
        }

        state.showBanner("Loading models...", PanelStyle.Info)
        scope.launch {
            runCatching {
                driver.listModels()
            }.onSuccess { models ->
                state.setModels(models)
                val title = "Models (${models.size})"
                val lines = if (models.isEmpty()) {
                    listOf("<none>")
                } else {
                    models.map { "• ${it.name} (v${it.version})" }
                }
                state.recordHistory(
                    label = commandLabel,
                    heading = title,
                    lines = lines,
                    style = PanelStyle.Info,
                )
                val summary = if (models.isEmpty()) "No models found" else "${models.size} models loaded"
                state.showBanner(summary, PanelStyle.Success)
            }.onFailure { error ->
                val message = "Failed to list models: ${error.message ?: error::class.simpleName}".trim()
                state.recordHistory(
                    label = commandLabel,
                    heading = "List failed",
                    lines = listOf(message),
                    style = PanelStyle.Error,
                )
                state.showBanner(message, PanelStyle.Error)
            }
        }
    }

    private fun openModelSelector() {
        val driver = state.driver()
        if (driver == null) {
            state.showBanner("No store connected. Complete the wizard first.", PanelStyle.Warning)
            return
        }

        scope.launch {
            try {
                val models = if (state.currentModels().isEmpty()) driver.listModels() else state.currentModels()
                if (models.isEmpty()) {
                    state.showBanner("No models found in store.", PanelStyle.Warning)
                    state.updateMode(UiMode.Prompt(input = ""))
                } else {
                    state.setModels(models)
                    state.updateMode(UiMode.ModelSelection(models.toList(), selectedIndex = 0))
                    state.showBanner("Select a model to inspect.", PanelStyle.Info)
                }
            } catch (e: Exception) {
                val message = "Failed to load models: ${e.message ?: e::class.simpleName}".trim()
                state.recordHistory(
                    label = "model",
                    heading = "Load models failed",
                    lines = listOf(message),
                    style = PanelStyle.Error,
                )
                state.showBanner(message, PanelStyle.Error)
            }
        }
    }

    private fun displayModel(commandLabel: String, name: String) {
        val driver = state.driver()
        if (driver == null) {
            val message = "No store connected. Complete the wizard first."
            state.showBanner(message, PanelStyle.Warning)
            state.recordHistory(
                label = commandLabel,
                heading = "No connection",
                lines = listOf(message),
                style = PanelStyle.Warning,
            )
            return
        }

        state.showBanner("Loading model '$name'...", PanelStyle.Info)
        scope.launch {
            runCatching {
                driver.loadModel(name)
            }.onSuccess { model ->
                if (model == null) {
                    val message = "Model '$name' not found."
                    state.recordHistory(
                        label = commandLabel,
                        heading = "Model missing",
                        lines = listOf(message),
                        style = PanelStyle.Warning,
                    )
                    state.showBanner(message, PanelStyle.Warning)
                } else {
                    val lines = renderModelDefinition(model)
                    val heading = lines.firstOrNull() ?: "Model ${model.name}"
                    val body = if (lines.size > 1) lines.drop(1) else emptyList()
                    state.recordHistory(
                        label = commandLabel,
                        heading = heading,
                        lines = body,
                        style = PanelStyle.Info,
                    )
                    state.showBanner("Model '${model.name}' loaded.", PanelStyle.Success)
                }
            }.onFailure { error ->
                val message = "Failed to load model '$name': ${error.message ?: error::class.simpleName}".trim()
                state.recordHistory(
                    label = commandLabel,
                    heading = "Model load failed",
                    lines = listOf(message),
                    style = PanelStyle.Error,
                )
                state.showBanner(message, PanelStyle.Error)
            }
        }
    }

    private fun buildConfig(storeType: StoreType, values: Map<String, String?>): StoreConnectionConfig? = when (storeType) {
        StoreType.RocksDb -> {
            val path = values["path"]?.trim().orEmpty()
            if (path.isBlank()) {
                state.showBanner("RocksDB path is required.", PanelStyle.Error)
                null
            } else {
                StoreConnectionConfig.RocksDb(path)
            }
        }
        StoreType.FoundationDb -> {
            val cluster = values["clusterFile"]?.trim().orEmpty().ifBlank { null }
            val tenantValue = values["tenant"]?.trim().orEmpty().ifBlank { null }
            val directoryRaw = values["directory"]?.trim().orEmpty().ifBlank { "maryk" }
            val directories = directoryRaw.split(Regex("[\\s,/]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val apiVersion = values["apiVersion"]?.trim().orEmpty().ifBlank { "730" }.toIntOrNull()
            if (apiVersion == null) {
                state.showBanner("Invalid API version. Provide a numeric value.", PanelStyle.Error)
                null
            } else {
                val tenant = tenantValue?.let { Tuple.from(it) }
                StoreConnectionConfig.FoundationDb(
                    clusterFile = cluster,
                    tenant = tenant,
                    directoryRoot = if (directories.isEmpty()) listOf("maryk") else directories,
                    apiVersion = apiVersion,
                )
            }
        }
    }

    private fun connect(config: StoreConnectionConfig) {
        state.isConnecting.value = true
        state.showBanner("Connecting to ${config.type.displayName}...", PanelStyle.Info)

        scope.launch {
            val driver = createStoreDriver(config)
            runCatching {
                driver.connect()
            }.onSuccess {
                state.driver()?.close()
                state.setDriver(driver)
                state.setModels(emptyList())
                state.connectionDescription.value = driver.description
                state.recordHistory(
                    label = "connect",
                    heading = "Connected",
                    lines = listOf("Connected to ${driver.description}"),
                    style = PanelStyle.Success,
                )
                state.showBanner("Connected to ${driver.description}", PanelStyle.Success)
                state.isConnecting.value = false
                state.updateMode(UiMode.Prompt(input = ""))
            }.onFailure { error ->
                state.isConnecting.value = false
                val message = "Failed to connect: ${error.message ?: error::class.simpleName}. Use the wizard to try again.".trim()
                state.recordHistory(
                    label = "connect",
                    heading = "Connection failed",
                    lines = listOf(message),
                    style = PanelStyle.Error,
                )
                state.showBanner(message, PanelStyle.Error)
                driver.close()
                beginWizard(showHint = false)
            }
        }
    }

    private fun exit(commandLabel: String?) {
        state.recordHistory(
            label = commandLabel,
            heading = "Closing terminal client",
            lines = listOf("Goodbye."),
            style = PanelStyle.Info,
        )
        state.showBanner("Closing terminal client...", PanelStyle.Info)
        state.shouldExit.value = true
        scope.launch {
            withContext(Dispatchers.IO) {
                state.close()
            }
            onExit()
        }
    }
}
