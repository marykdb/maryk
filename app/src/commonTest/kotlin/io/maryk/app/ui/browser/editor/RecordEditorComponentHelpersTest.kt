package io.maryk.app.ui.browser.editor

import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.numeric.UInt32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordEditorComponentHelpersTest {
    @Test
    fun shouldUseMultilineTextEditorForLargeStrings() {
        val large = StringDefinition(maxSize = 500u)
        val small = StringDefinition(maxSize = 499u)
        val number = NumberDefinition(type = UInt32)

        assertTrue(shouldUseMultilineTextEditor("Description", large))
        assertFalse(shouldUseMultilineTextEditor("Description", small))
        assertFalse(shouldUseMultilineTextEditor("Description", number))
    }

    @Test
    fun shouldUseMultilineTextEditorSkipsKeyLabels() {
        val large = StringDefinition(maxSize = 500u)

        assertFalse(shouldUseMultilineTextEditor("Key 1", large))
        assertFalse(shouldUseMultilineTextEditor("key something", large))
    }

    @Test
    fun buildEditorHeaderLabelRendersRequiredAndCount() {
        assertEquals("Items * (3)", buildEditorHeaderLabel("Items", required = true, countLabel = "3"))
        assertEquals("Items", buildEditorHeaderLabel("Items", required = false, countLabel = null))
        assertEquals("Items *", buildEditorHeaderLabel("Items", required = true, countLabel = ""))
    }

    @Test
    fun mapContentStartIndentComputesExpectedOffsets() {
        assertEquals(140, mapContentStartIndent(indent = 0, isIncMap = true))
        assertEquals(148, mapContentStartIndent(indent = 0, isIncMap = false))
        assertEquals(176, mapContentStartIndent(indent = 3, isIncMap = true))
        assertEquals(184, mapContentStartIndent(indent = 3, isIncMap = false))
    }

    @Test
    fun referenceInfoTooltipTextReflectsReferenceState() {
        assertEquals(
            "No reference.",
            referenceInfoTooltipText(
                referenceKeyText = "",
                loading = false,
                error = null,
                preview = null,
            ),
        )
        assertEquals(
            "Loading…",
            referenceInfoTooltipText(
                referenceKeyText = "abc-123",
                loading = true,
                error = null,
                preview = null,
            ),
        )
        assertEquals(
            "Invalid key.",
            referenceInfoTooltipText(
                referenceKeyText = "abc-123",
                loading = false,
                error = "Invalid key.",
                preview = null,
            ),
        )
        assertEquals(
            "key: value",
            referenceInfoTooltipText(
                referenceKeyText = "abc-123",
                loading = false,
                error = null,
                preview = "key: value",
            ),
        )
    }

    @Test
    fun buildReferencePreviewYamlTruncatesLineOverflow() {
        val yaml = (1..12).joinToString(separator = "\n") { "line$it" }
        assertEquals((1..10).joinToString(separator = "\n") { "line$it" } + "\n…", buildReferencePreviewYaml(yaml, maxLines = 10))
        assertEquals("line1\nline2", buildReferencePreviewYaml("line1\nline2", maxLines = 10))
    }

    @Test
    fun isUnsetLikeMultiTypeValueTreatsEmptyCollectionsAndTextAsUnset() {
        val stringDefinition = StringDefinition(minSize = 1u)
        val listDefinition = ListDefinition(
            required = false,
            valueDefinition = StringDefinition(),
        )
        val setDefinition = SetDefinition(
            required = false,
            valueDefinition = StringDefinition(),
        )
        val mapDefinition = MapDefinition(
            required = false,
            keyDefinition = StringDefinition(),
            valueDefinition = StringDefinition(),
        )

        assertTrue(isUnsetLikeMultiTypeValue(stringDefinition, ""))
        assertTrue(isUnsetLikeMultiTypeValue(listDefinition, emptyList<String>()))
        assertTrue(isUnsetLikeMultiTypeValue(setDefinition, emptySet<String>()))
        assertTrue(isUnsetLikeMultiTypeValue(mapDefinition, emptyMap<String, String>()))
        assertFalse(isUnsetLikeMultiTypeValue(stringDefinition, "set"))
    }

    @Test
    fun canChangeMultiTypeSelectionAllowsTypeFinalWhenValueUnset() {
        val definition = StringDefinition()

        assertTrue(
            canChangeMultiTypeSelection(
                enabled = true,
                typeIsFinal = true,
                currentSubDefinition = definition,
                currentValue = "",
            ),
        )
        assertFalse(
            canChangeMultiTypeSelection(
                enabled = true,
                typeIsFinal = true,
                currentSubDefinition = definition,
                currentValue = "set",
            ),
        )
        assertTrue(
            canChangeMultiTypeSelection(
                enabled = true,
                typeIsFinal = false,
                currentSubDefinition = definition,
                currentValue = "set",
            ),
        )
        assertFalse(
            canChangeMultiTypeSelection(
                enabled = false,
                typeIsFinal = false,
                currentSubDefinition = definition,
                currentValue = "",
            ),
        )
    }
}
