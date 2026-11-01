package io.maryk.app.data

import kotlinx.datetime.LocalTime
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.models.DataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.map
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
            ScanQueryParser.parseSelectGraph(ScanParserModel, listOf("map[12:34:56]"))
        )

        assertTrue(graph.contains(ScanParserModel.ref { map.at(LocalTime(12, 34, 56)) }))
    }

    @Test
    fun parseSelectGraphRejectsMalformedPaths() {
        assertNull(ScanQueryParser.parseSelectGraph(ScanParserModel, listOf(" ")))

        listOf(
            ".string",
            "embeddedValues..value",
            "map[12:34:56",
            "map]12:34:56[",
        ).forEach { path ->
            assertFailsWith<IllegalArgumentException> {
                ScanQueryParser.parseSelectGraph(ScanParserModel, listOf(path))
            }
        }
    }
}

private object ScanParserModel : RootDataModel<ScanParserModel>() {
    val string by string(
        index = 1u,
    )

    val map by map(
        index = 2u,
        keyDefinition = TimeDefinition(),
        valueDefinition = StringDefinition(),
    )
}

private object ToolSelectionDetails : DataModel<ToolSelectionDetails>() {
    val first by string(1u)
    val second by string(2u)
}

private object ToolSelectionRoot : RootDataModel<ToolSelectionRoot>() {
    val details by embed(1u, dataModel = { ToolSelectionDetails })
}
