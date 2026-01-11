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

enum class GridDensity {
    COMPACT,
    STANDARD,
    COMFY,
}

@Stable
class BrowserUiState {
    var showCatalog by mutableStateOf(AppPreferences.getBoolean(keyCatalogOpen, true))
        private set

    var showInspector by mutableStateOf(AppPreferences.getBoolean(keyInspectorOpen, false))
        private set

    var gridDensity by mutableStateOf(parseDensity(AppPreferences.getString(keyGridDensity, GridDensity.COMPACT.name)))
        private set

    var lastSelectedModelId by mutableStateOf(readLastModel())
        private set

    var lastRefreshLabel by mutableStateOf("Idle")
        private set

    val selectedRowKeys = mutableStateMapOf<Key<IsRootDataModel>, Boolean>()

    val columnVisibility = mutableStateMapOf<String, Set<String>>()

    init {
        readColumns().forEach { (key, value) ->
            columnVisibility[key] = value
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

    fun setLastModel(modelId: UInt?) {
        lastSelectedModelId = modelId
        if (modelId != null) {
            AppPreferences.putString(keyLastModel, modelId.toString())
        }
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

    private fun readLastModel(): UInt? {
        val raw = AppPreferences.getString(keyLastModel, "")
        return raw.toUIntOrNull()
    }

    private fun parseDensity(value: String): GridDensity {
        return runCatching { GridDensity.valueOf(value) }.getOrDefault(GridDensity.STANDARD)
    }
}
