package io.maryk.app.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.maryk.app.config.AppPreferences
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

    var lastRefreshLabel by mutableStateOf("Idle")
        private set

    val selectedRowKeys = mutableStateMapOf<Key<IsRootDataModel>, Boolean>()

    val columnVisibility = mutableStateMapOf<String, Set<String>>()
    val pinnedProperties = mutableStateMapOf<String, Set<String>>()

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

    fun lastSelectedModelId(scopeKey: String?): UInt? {
        return readLastModel(scopeKey)
    }

    fun setLastModel(scopeKey: String?, modelId: UInt?) {
        val key = lastModelKey(scopeKey)
        if (modelId != null) {
            AppPreferences.putString(key, modelId.toString())
        } else {
            AppPreferences.putString(key, "")
            AppPreferences.putString(keyLastModel, "")
        }
    }

    fun pinnedPaths(scopeKey: String?, modelId: UInt?): Set<String> {
        if (modelId == null) return emptySet()
        val scopedKey = pinnedKey(scopeKey, modelId)
        pinnedProperties[scopedKey]?.let { return it }
        return pinnedProperties[legacyPinnedKey(modelId)].orEmpty()
    }

    fun isPinned(scopeKey: String?, modelId: UInt?, path: String): Boolean {
        if (modelId == null) return false
        return pinnedPaths(scopeKey, modelId).contains(path)
    }

    fun togglePinned(scopeKey: String?, modelId: UInt?, path: String) {
        if (modelId == null) return
        val key = pinnedKey(scopeKey, modelId)
        val current = pinnedProperties[key]?.toMutableSet()
            ?: pinnedProperties[legacyPinnedKey(modelId)]?.toMutableSet()
            ?: mutableSetOf()
        if (!current.add(path)) {
            current.remove(path)
        }
        if (current.isEmpty()) {
            pinnedProperties.remove(key)
            pinnedProperties.remove(legacyPinnedKey(modelId))
        } else {
            pinnedProperties[key] = current
            pinnedProperties.remove(legacyPinnedKey(modelId))
        }
        persistPinned()
    }

    fun ensureAutoPins(scopeKey: String?, modelId: UInt?, paths: Set<String>) {
        if (modelId == null) return
        val key = pinnedKey(scopeKey, modelId)
        val legacyKey = legacyPinnedKey(modelId)
        if (pinnedProperties[key]?.isNotEmpty() == true) {
            if (pinnedProperties.remove(legacyKey) != null) {
                persistPinned()
            }
            return
        }
        val legacy = pinnedProperties[legacyKey]
        if (legacy?.isNotEmpty() == true) {
            pinnedProperties[key] = legacy
            pinnedProperties.remove(legacyKey)
            persistPinned()
            return
        }
        if (paths.isEmpty()) return
        pinnedProperties[key] = paths
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

    private fun readPinned(): Map<String, Set<String>> {
        val raw = AppPreferences.getString(keyPinned, "")
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

    private fun persistPinned() {
        val raw = pinnedProperties.entries.joinToString(";") { entry ->
            val values = entry.value.joinToString(",")
            "${entry.key}=$values"
        }
        AppPreferences.putString(keyPinned, raw)
    }

    private fun pinnedKey(scopeKey: String?, modelId: UInt): String {
        val scope = scopeKey?.takeIf { it.isNotBlank() } ?: "default"
        return "$scope:$modelId"
    }

    private fun legacyPinnedKey(modelId: UInt): String = modelId.toString()

    private fun readLastModel(scopeKey: String?): UInt? {
        val scopedKey = lastModelKey(scopeKey)
        val scoped = AppPreferences.getString(scopedKey, "").toUIntOrNull()
        if (scoped != null) {
            if (AppPreferences.getString(keyLastModel, "").isNotBlank()) {
                AppPreferences.putString(keyLastModel, "")
            }
            return scoped
        }
        val legacy = AppPreferences.getString(keyLastModel, "").toUIntOrNull() ?: return null
        AppPreferences.putString(scopedKey, legacy.toString())
        AppPreferences.putString(keyLastModel, "")
        return legacy
    }

    private fun lastModelKey(scopeKey: String?): String {
        val scope = scopeKey?.takeIf { it.isNotBlank() } ?: "default"
        return "$keyLastModel.$scope"
    }

    private fun readResultsTab(): ResultsTab {
        val raw = AppPreferences.getString(keyResultsTab, ResultsTab.DATA.name)
        return enumValueOrDefault(raw, ResultsTab.DATA)
    }

    private fun parseDensity(value: String): GridDensity {
        return enumValueOrDefault(value, GridDensity.STANDARD)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        try {
            enumValueOf<T>(value)
        } catch (_: IllegalArgumentException) {
            default
        }
}
