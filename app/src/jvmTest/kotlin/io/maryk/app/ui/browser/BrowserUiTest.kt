package io.maryk.app.ui.browser

import io.maryk.app.state.ModelEntry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserUiTest {
    @Test
    fun shouldRestoreLastModelReturnsFalseWhenModelListIsEmpty() {
        assertFalse(
            shouldRestoreLastModel(
                lastSelectedModelId = 42u,
                models = emptyList(),
            )
        )
    }

    @Test
    fun shouldRestoreLastModelReturnsTrueWhenModelExists() {
        assertTrue(
            shouldRestoreLastModel(
                lastSelectedModelId = 42u,
                models = listOf(ModelEntry(name = "Test", id = 42u)),
            )
        )
    }
}
