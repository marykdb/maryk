package io.maryk.cli

import kotlinx.datetime.LocalTime
import maryk.test.models.TestMarykModel
import maryk.core.models.RootDataModel
import maryk.core.models.DataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanQueryParserTest {
    @Test
    fun nestedAndWholeParentSelectionsKeepTheirMeaning() {
        val selected = assertNotNull(ScanQueryParser.parseSelectGraph(ToolSelectionRoot, listOf("details.first")))
        assertTrue(selected.contains(ToolSelectionRoot.ref { details { first } }))
        for (paths in listOf(listOf("details", "details.first"), listOf("details.first", "details"))) {
            val whole = assertNotNull(ScanQueryParser.parseSelectGraph(ToolSelectionRoot, paths))
            assertTrue(whole.contains(ToolSelectionRoot.ref { details { second } }))
        }
    }

    @Test
    fun parseSelectGraphKeepsDotsInsideMapKeys() {
        val graph = assertNotNull(
            ScanQueryParser.parseSelectGraph(TestMarykModel, listOf("map[12:34:56]"))
        )

        assertTrue(graph.contains(TestMarykModel.ref { map.at(LocalTime(12, 34, 56)) }))
    }

    @Test
    fun parseSelectGraphRejectsMalformedPaths() {
        assertNull(ScanQueryParser.parseSelectGraph(TestMarykModel, listOf(" ")))

        listOf(
            ".string",
            "embeddedValues..value",
            "map[12:34:56",
            "map]12:34:56[",
        ).forEach { path ->
            assertFailsWith<IllegalArgumentException> {
                ScanQueryParser.parseSelectGraph(TestMarykModel, listOf(path))
            }
        }
    }
}

private object ToolSelectionDetails : DataModel<ToolSelectionDetails>() {
    val first by string(1u)
    val second by string(2u)
}

private object ToolSelectionRoot : RootDataModel<ToolSelectionRoot>() {
    val details by embed(1u, dataModel = { ToolSelectionDetails })
}
