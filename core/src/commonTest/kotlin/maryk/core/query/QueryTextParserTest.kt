package maryk.core.query

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.orders.Order
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object SelectionDetails : DataModel<SelectionDetails>() {
    val first by string(1u)
    val second by string(2u)
}

private object SelectionRecord : RootDataModel<SelectionRecord>() {
    val details by embed(1u, dataModel = { SelectionDetails })
    val entries by map(2u, keyDefinition = StringDefinition(),
        valueDefinition = EmbeddedValuesDefinition(dataModel = { SelectionDetails }))
}

class QueryTextParserTest {
    @Test
    fun nestedSelectionIncludesOnlyRequestedFields() {
        val graph = assertNotNull(QueryTextParser.parseSelectGraph(SelectionRecord, listOf("details.first")))
        assertTrue(graph.contains(SelectionRecord.ref { details { first } }))
        assertFalse(graph.contains(SelectionRecord.ref { details { second } }))
    }

    @Test
    fun wholeParentWinsRegardlessOfInputOrder() {
        for (paths in listOf(listOf("details", "details.first"), listOf("details.first", "details"))) {
            val graph = assertNotNull(QueryTextParser.parseSelectGraph(SelectionRecord, paths))
            assertTrue(graph.contains(SelectionRecord.ref { details { first } }))
            assertTrue(graph.contains(SelectionRecord.ref { details { second } }))
        }
    }

    @Test
    fun mapKeysAreEscapedAndChildSelectionsStayInTheirEntry() {
        val key = "a.b[1]\"'"
        val graph = assertNotNull(QueryTextParser.parseSelectGraph(SelectionRecord, listOf("entries[$key].first")))
        val entry = SelectionRecord.ref { entries at key }
        assertTrue(graph.contains(SelectionDetails.ref(entry) { first }))
        assertFalse(graph.contains(SelectionDetails.ref(entry) { second }))
        assertFalse(graph.contains(SelectionDetails.ref(SelectionRecord.ref { entries at "other" }) { first }))
    }

    @Test
    fun commaSeparatedPathsPreserveMapKeysAndNestedSelections() {
        val key = "a,b[1].c"
        val paths = QueryTextParser.parseReferencePaths(listOf(" entries[$key].first, details.second ", ",, "))
        assertEquals(listOf("entries[$key].first", "details.second"), paths)
        val graph = assertNotNull(QueryTextParser.parseSelectGraph(SelectionRecord, paths))
        val entry = SelectionRecord.ref { entries at key }
        assertTrue(graph.contains(SelectionDetails.ref(entry) { first }))
        assertFalse(graph.contains(SelectionDetails.ref(entry) { second }))
        assertTrue(graph.contains(SelectionRecord.ref { details { second } }))
    }

    @Test
    fun commaSeparatedPathsRejectUnbalancedBrackets() {
        for (path in listOf("entries[missing,details.first", "entries]bad[,details.first")) {
            assertFailsWith<IllegalArgumentException> { QueryTextParser.parseReferencePaths(listOf(path)) }
        }
    }

    @Test
    fun malformedPathsAreRejectedAndEmptySelectionIsAbsent() {
        assertNull(QueryTextParser.parseSelectGraph(SelectionRecord, listOf(" ")))
        for (path in listOf(".details", "details..first", "entries[missing", "entries]bad[")) {
            assertFailsWith<IllegalArgumentException> { QueryTextParser.parseSelectGraph(SelectionRecord, listOf(path)) }
        }
    }

    @Test
    fun filtersUseTheModelCodecForNestedReferences() {
        assertEquals(Equals(SelectionRecord.ref { details { first } } with "Ada"),
            QueryTextParser.parseFilter(SelectionRecord, "!Equals { details.first: Ada }"))
    }

    @Test
    fun toolsSharePrimaryKeyOrdering() {
        assertEquals(Order.ascending, QueryTextParser.parseOrder(SelectionRecord, listOf("\$key")))
        assertEquals(Order.descending, QueryTextParser.parseOrder(SelectionRecord, listOf("-\$key")))
    }
}
