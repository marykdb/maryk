package io.maryk.app.ui.browser.editor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordEditorDialogTest {
    @Test
    fun isSaveFailureMessageDetectsFailurePatterns() {
        assertTrue(isSaveFailureMessage("Add failed: boom"))
        assertTrue(isSaveFailureMessage("Update FAILED: boom"))
        assertTrue(isSaveFailureMessage("Time travel mode active."))
    }

    @Test
    fun isSaveFailureMessageAllowsSuccessPatterns() {
        assertFalse(isSaveFailureMessage("Added Model key."))
        assertFalse(isSaveFailureMessage("Updated Model key."))
    }
}
