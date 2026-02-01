package io.maryk.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key

private const val keyCatalogOpen = "ui.catalog.open"
private const val keyInspectorOpen = "ui.inspector.open"
private const val keyGridDensity = "ui.grid.density"
private const val keyColumns = "ui.grid.columns"
private const val keyLastModel = "ui.last.model"
private const val keyResultsTab = "ui.results.tab"
private const val keyPinned = "ui.model.pins"

enum class GridDensity {
    COMPACT,
    STANDARD,
    COMFY,
}

enum class ResultsTab(val label: String) {
    DATA("Data"),
    AGGREGATE("Aggregate"),
    MODEL("Model"),
}

@Stable
class BrowserUiState {
    var showCatalog by mutableStateOf(AppPreferences.getBoolean(keyCatalogOpen, true))
        private set

    var showInspector by mutableStateOf(AppPreferences.getBoolean(keyInspectorOpen, false))
        private set

    var gridDensity by mutableStateOf(parseDensity(AppPreferences.getString(keyGridDensity, GridDensity.COMPACT.name)))
        private set

    var resultsTab by mutableStateOf(readResultsTab())
        private set

    var lastSelectedModelId by mutableStateOf(readLastModel())
        private set

    var lastRefreshLabel by mutableStateOf("Idle")
        private set

    val selectedRowKeys = mutableStateMapOf<Key<IsRootDataModel>, Boolean>()

    val columnVisibility = mutableStateMapOf<String, Set<String>>()
    val pinnedProperties = mutableStateMapOf<UInt, Set<String>>()

    init {
        readColumns().forEach { (key, value) ->
            columnVisibility[key] = value
        }
        readPinned().forEach { (key, value) ->
            pinnedProperties[key] = value
        }
    }

    fun toggleCatalog() {
        showCatalog = !showCatalog
        AppPreferences.putBoolean(keyCatalogOpen, showCatalog)
    }

    fun toggleInspector() {
        showInspector = !showInspector
        AppPreferences.putBoolean(keyInspectorOpen, showInspector)
    }

    fun setInspectorOpen(open: Boolean) {
        showInspector = open
        AppPreferences.putBoolean(keyInspectorOpen, open)
    }

    fun markRefreshed(label: String) {
        lastRefreshLabel = label
    }

    fun updateResultsTab(tab: ResultsTab) {
        resultsTab = tab
        AppPreferences.putString(keyResultsTab, tab.name)
    }

    fun setLastModel(modelId: UInt?) {
        lastSelectedModelId = modelId
        if (modelId != null) {
            AppPreferences.putString(keyLastModel, modelId.toString())
        }
    }

    fun pinnedPaths(modelId: UInt?): Set<String> {
        if (modelId == null) return emptySet()
        return pinnedProperties[modelId].orEmpty()
    }

    fun isPinned(modelId: UInt?, path: String): Boolean {
        if (modelId == null) return false
        return pinnedProperties[modelId]?.contains(path) == true
    }

    fun togglePinned(modelId: UInt?, path: String) {
        if (modelId == null) return
        val current = pinnedProperties[modelId]?.toMutableSet() ?: mutableSetOf()
        if (!current.add(path)) {
            current.remove(path)
        }
        if (current.isEmpty()) {
            pinnedProperties.remove(modelId)
        } else {
            pinnedProperties[modelId] = current
        }
        persistPinned()
    }

    fun ensureAutoPins(modelId: UInt?, paths: Set<String>) {
        if (modelId == null) return
        if (pinnedProperties[modelId]?.isNotEmpty() == true) return
        if (paths.isEmpty()) return
        pinnedProperties[modelId] = paths
        persistPinned()
    }

    private fun readColumns(): Map<String, Set<String>> {
        val raw = AppPreferences.getString(keyColumns, "")
        if (raw.isBlank()) return emptyMap()
        return raw.split(";")
            .mapNotNull { entry ->
                val parts = entry.split("=")
                if (parts.size != 2) return@mapNotNull null
                val key = parts[0]
                val values = parts[1].split(",").filter { it.isNotBlank() }.toSet()
                key to values
            }
            .toMap()
    }

    private fun readPinned(): Map<UInt, Set<String>> {
        val raw = AppPreferences.getString(keyPinned, "")
        if (raw.isBlank()) return emptyMap()
        return raw.split(";")
            .mapNotNull { entry ->
                val parts = entry.split("=")
                if (parts.size != 2) return@mapNotNull null
                val modelId = parts[0].toUIntOrNull() ?: return@mapNotNull null
                val values = parts[1].split(",").filter { it.isNotBlank() }.toSet()
                modelId to values
            }
            .toMap()
    }

    private fun persistPinned() {
        val raw = pinnedProperties.entries.joinToString(";") { entry ->
            val values = entry.value.joinToString(",")
            "${entry.key}=$values"
        }
        AppPreferences.putString(keyPinned, raw)
    }

    private fun readLastModel(): UInt? {
        val raw = AppPreferences.getString(keyLastModel, "")
        return raw.toUIntOrNull()
    }

    private fun readResultsTab(): ResultsTab {
        val raw = AppPreferences.getString(keyResultsTab, ResultsTab.DATA.name)
        return runCatching { ResultsTab.valueOf(raw) }.getOrDefault(ResultsTab.DATA)
    }

    private fun parseDensity(value: String): GridDensity {
        return runCatching { GridDensity.valueOf(value) }.getOrDefault(GridDensity.STANDARD)
    }
}