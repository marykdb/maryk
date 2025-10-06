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
import maryk.datastore.terminal.renderModelDefinition

private const val MAX_LOG_LINES = 200

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
    val logLines = mutableStateListOf<String>()
    val isConnecting = mutableStateOf(false)
    val connectionDescription = mutableStateOf<String?>(null)
    val shouldExit = mutableStateOf(false)
    val outputTitle = mutableStateOf<String?>(null)
    val outputLines = mutableStateListOf<String>()

    private val models = mutableStateListOf<StoredModel>()
    private val driver = mutableStateOf<StoreDriver?>(null)

    fun updateMode(mode: UiMode) {
        this.mode.value = mode
    }

    fun appendLog(line: String) {
        if (logLines.size >= MAX_LOG_LINES) {
            logLines.removeFirst()
        }
        logLines.add(line)
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

    fun showOutput(title: String?, lines: List<String>) {
        outputTitle.value = title
        outputLines.clear()
        outputLines.addAll(lines)
    }

    fun clearOutput() {
        outputTitle.value = null
        outputLines.clear()
    }

    fun close() {
        driver.value?.close()
        models.clear()
        clearOutput()
    }
}

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
        "exit" to "exit - Close the terminal client.",
    )

    fun startWithConfig(config: StoreConnectionConfig) {
        state.updateMode(UiMode.Prompt(input = ""))
        state.clearOutput()
        connect(config)
    }

    fun beginWizard() {
        state.updateMode(UiMode.SelectStore(selectedIndex = 0))
        state.clearOutput()
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
                        state.appendLog("${mode.currentField.label} is required.")
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
            "Enter" -> {
                val command = mode.input.trim()
                state.updateMode(mode.copy(input = ""))
                if (command.isNotEmpty()) {
                    state.clearOutput()
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
                    state.clearOutput()
                    displayModel(model.name)
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
        val parts = command.split(Regex("\\s+"), limit = 2)
        val action = parts.first().lowercase()
        val argument = parts.getOrNull(1)?.trim().orEmpty()

        if (state.isConnecting.value) {
            state.appendLog("Connection in progress. Please wait...")
            return
        }

        when (action) {
            "help", "h" -> showHelp(argument)
            "list", "l" -> listModels()
            "model" -> {
                if (argument.isNotEmpty()) {
                    displayModel(argument)
                } else {
                    openModelSelector()
                }
            }
            "exit" -> exit()
            "connect" -> {
                state.appendLog("Reconnect is not supported from the prompt. Restart the client to choose another store.")
            }
            else -> state.appendLog("Unknown command '$action'. Type 'help' to see available commands.")
        }
    }

    private fun showHelp(argument: String) {
        if (argument.isBlank()) {
            state.showOutput("Commands", helpEntries.values.map { it })
        } else {
            val key = argument.lowercase()
            val match = helpEntries.entries.firstOrNull { (cmd, _) ->
                cmd == key || cmd.startsWith(key)
            }
            if (match != null) {
                state.showOutput("Help: ${match.key}", listOf(match.value))
            } else {
                state.showOutput("Help", listOf("No help available for '$argument'."))
            }
        }
    }

    private fun listModels() {
        val driver = state.driver()
        if (driver == null) {
            state.appendLog("No store connected. Complete the wizard first.")
            return
        }

        scope.launch {
            try {
                val models = driver.listModels()
                state.setModels(models)
                val title = "Models (${models.size})"
                val lines = if (models.isEmpty()) {
                    listOf("<none>")
                } else {
                    models.map { "• ${it.name} (v${it.version})" }
                }
                state.showOutput(title, lines)
            } catch (e: Exception) {
                state.appendLog("Failed to list models: ${e.message}")
            }
        }
    }

    private fun openModelSelector() {
        val driver = state.driver()
        if (driver == null) {
            state.appendLog("No store connected. Complete the wizard first.")
            return
        }

        scope.launch {
            try {
                val models = if (state.currentModels().isEmpty()) driver.listModels() else state.currentModels()
                if (models.isEmpty()) {
                    state.appendLog("No models found in store.")
                    state.updateMode(UiMode.Prompt(input = ""))
                } else {
                    state.setModels(models)
                    state.updateMode(UiMode.ModelSelection(models.toList(), selectedIndex = 0))
                }
            } catch (e: Exception) {
                state.appendLog("Failed to load models: ${e.message}")
            }
        }
    }

    private fun displayModel(name: String) {
        val driver = state.driver()
        if (driver == null) {
            state.appendLog("No store connected. Complete the wizard first.")
            return
        }

        scope.launch {
            try {
                val model = driver.loadModel(name)
                if (model == null) {
                    state.appendLog("Model '$name' not found.")
                } else {
                    val lines = renderModelDefinition(model)
                    val title = lines.firstOrNull()
                    val body = if (lines.size > 1) lines.drop(1) else emptyList()
                    state.showOutput(title, body)
                }
            } catch (e: Exception) {
                state.appendLog("Failed to load model '$name': ${e.message}")
            }
        }
    }

    private fun buildConfig(storeType: StoreType, values: Map<String, String?>): StoreConnectionConfig? = when (storeType) {
        StoreType.RocksDb -> {
            val path = values["path"]?.trim().orEmpty()
            if (path.isBlank()) {
                state.appendLog("RocksDB path is required.")
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
                state.appendLog("Invalid API version. Provide a numeric value.")
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
        state.clearOutput()
        state.appendLog("Connecting to ${config.type.displayName}...")

        scope.launch {
            val driver = createStoreDriver(config)
            runCatching {
                driver.connect()
            }.onSuccess {
                state.driver()?.close()
                state.setDriver(driver)
                state.setModels(emptyList())
                state.connectionDescription.value = driver.description
                state.appendLog("Connected to ${driver.description}")
                state.isConnecting.value = false
                state.clearOutput()
                state.updateMode(UiMode.Prompt(input = ""))
            }.onFailure { error ->
                state.isConnecting.value = false
                state.appendLog("Failed to connect: ${error.message}")
                driver.close()
                beginWizard()
            }
        }
    }

    private fun exit() {
        state.appendLog("Closing terminal client.")
        state.shouldExit.value = true
        scope.launch {
            withContext(Dispatchers.IO) {
                state.close()
            }
            onExit()
        }
    }
}
