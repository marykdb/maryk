package io.maryk.app.state

import io.maryk.app.config.AppPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BrowserUiStateTest {
    @Test
    fun setLastModelNullClearsPersistedSelectionForScope() {
        val scope = "scope-a"
        val original = BrowserUiState().lastSelectedModelId(scope)
        try {
            val state = BrowserUiState()
            state.setLastModel(scope, 123u)
            assertEquals(123u, BrowserUiState().lastSelectedModelId(scope))

            state.setLastModel(scope, null)
            assertNull(BrowserUiState().lastSelectedModelId(scope))
        } finally {
            BrowserUiState().setLastModel(scope, original)
        }
    }

    @Test
    fun lastModelSelectionIsScopedPerConnection() {
        val state = BrowserUiState()
        val scopeA = "scope-a"
        val scopeB = "scope-b"
        val originalA = state.lastSelectedModelId(scopeA)
        val originalB = state.lastSelectedModelId(scopeB)
        try {
            state.setLastModel(scopeA, 11u)
            state.setLastModel(scopeB, 22u)

            assertEquals(11u, BrowserUiState().lastSelectedModelId(scopeA))
            assertEquals(22u, BrowserUiState().lastSelectedModelId(scopeB))
        } finally {
            state.setLastModel(scopeA, originalA)
            state.setLastModel(scopeB, originalB)
        }
    }

    @Test
    fun legacyLastModelSelectionMigratesOnceAndClearsGlobalFallback() {
        val scopeA = "legacy-scope-a"
        val scopeB = "legacy-scope-b"
        val scopedKeyA = "ui.last.model.$scopeA"
        val scopedKeyB = "ui.last.model.$scopeB"
        val legacyKey = "ui.last.model"
        val originalScopedA = AppPreferences.getString(scopedKeyA, "")
        val originalScopedB = AppPreferences.getString(scopedKeyB, "")
        val originalLegacy = AppPreferences.getString(legacyKey, "")
        try {
            AppPreferences.putString(scopedKeyA, "")
            AppPreferences.putString(scopedKeyB, "")
            AppPreferences.putString(legacyKey, "44")

            assertEquals(44u, BrowserUiState().lastSelectedModelId(scopeA))
            assertNull(BrowserUiState().lastSelectedModelId(scopeB))
            assertEquals("", AppPreferences.getString(legacyKey, ""))
        } finally {
            AppPreferences.putString(scopedKeyA, originalScopedA)
            AppPreferences.putString(scopedKeyB, originalScopedB)
            AppPreferences.putString(legacyKey, originalLegacy)
        }
    }

    @Test
    fun pinnedPathsAreScopedPerConnection() {
        val state = BrowserUiState()
        val scopeA = "scope-a"
        val scopeB = "scope-b"
        val modelId = 7u
        val path = "alpha"
        val originalA = state.pinnedPaths(scopeA, modelId)
        val originalB = state.pinnedPaths(scopeB, modelId)
        try {
            if (path in originalA) {
                state.togglePinned(scopeA, modelId, path)
            }
            if (path in originalB) {
                state.togglePinned(scopeB, modelId, path)
            }
            state.togglePinned(scopeA, modelId, path)
            assertEquals(setOf(path), BrowserUiState().pinnedPaths(scopeA, modelId))
            assertFalse(BrowserUiState().pinnedPaths(scopeB, modelId).contains(path))
        } finally {
            if (BrowserUiState().pinnedPaths(scopeA, modelId).contains(path)) {
                state.togglePinned(scopeA, modelId, path)
            }
            originalA.forEach { path -> state.togglePinned(scopeA, modelId, path) }
            originalB.forEach { path -> state.togglePinned(scopeB, modelId, path) }
        }
    }

    @Test
    fun legacyPinnedPathsStillLoadForExistingUsers() {
        val scopeA = "legacy-scope-a"
        val scopeB = "legacy-scope-b"
        val modelId = 9u
        val legacyKey = "ui.model.pins"
        val original = AppPreferences.getString(legacyKey, "")
        try {
            AppPreferences.putString(legacyKey, "$modelId=legacy-pin")

            val uiState = BrowserUiState()
            uiState.ensureAutoPins(scopeA, modelId, setOf("legacy-pin"))

            assertEquals(setOf("legacy-pin"), uiState.pinnedPaths(scopeA, modelId))
            assertEquals(true, BrowserUiState().isPinned(scopeA, modelId, "legacy-pin"))
            assertEquals(emptySet(), BrowserUiState().pinnedPaths(scopeB, modelId))
        } finally {
            AppPreferences.putString(legacyKey, original)
        }
    }

    @Test
    fun pinnedPathsLookupDoesNotMutateLegacyStorage() {
        val scope = "legacy-scope"
        val modelId = 12u
        val legacyKey = "ui.model.pins"
        val scopedKey = "ui.model.pins.$scope:$modelId"
        val originalLegacy = AppPreferences.getString(legacyKey, "")
        val originalScoped = AppPreferences.getString(scopedKey, "")
        try {
            AppPreferences.putString(legacyKey, "$modelId=legacy-pin")
            AppPreferences.putString(scopedKey, "")

            assertEquals(setOf("legacy-pin"), BrowserUiState().pinnedPaths(scope, modelId))
            assertEquals("$modelId=legacy-pin", AppPreferences.getString(legacyKey, ""))
            assertEquals("", AppPreferences.getString(scopedKey, ""))
        } finally {
            AppPreferences.putString(legacyKey, originalLegacy)
            AppPreferences.putString(scopedKey, originalScoped)
        }
    }
}
