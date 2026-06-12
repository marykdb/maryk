package io.maryk.cli

import kotlinx.datetime.LocalTime
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanQueryParserTest {
    @Test
    fun parseSelectGraphKeepsDotsInsideMapKeys() {
        val graph = assertNotNull(
            ScanQueryParser.parseSelectGraph(TestMarykModel, listOf("map[12:34:56]"))
        )

        assertTrue(graph.contains(TestMarykModel { map.refAt(LocalTime(12, 34, 56)) }))
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
