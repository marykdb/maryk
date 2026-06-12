package io.maryk.app.data

import kotlinx.datetime.LocalTime
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanQueryParserTest {
    @Test
    fun parseSelectGraphKeepsDotsInsideMapKeys() {
        val graph = assertNotNull(
            ScanQueryParser.parseSelectGraph(ScanParserModel, listOf("map[12:34:56]"))
        )

        assertTrue(graph.contains(ScanParserModel { map.refAt(LocalTime(12, 34, 56)) }))
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
