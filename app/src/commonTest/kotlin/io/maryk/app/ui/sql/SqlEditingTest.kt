package io.maryk.app.ui.sql

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.models.DataModel
import maryk.core.properties.definitions.string
import maryk.sql.SqlCatalog
import maryk.sql.SqlTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object SqlEditorDetail : DataModel<SqlEditorDetail>() {
    val label by string(1u, required = false)
}

private object SqlEditorPerson : RootDataModel<SqlEditorPerson>() {
    val name by string(1u)
    val nickname by string(2u, required = false)
    val details by map(3u, required = false, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { SqlEditorDetail }))
}

class SqlEditingTest {
    private val catalog = SqlCatalog(listOf(SqlTable("Person", SqlEditorPerson), SqlTable("odd\"table", SqlEditorPerson, "other")))

    @Test fun lexerHighlightsIncompleteSqlAndCountsOnlyRealParameters() {
        val tokens = sqlEditorTokens("SELECT ?, 'it''s ?', \"?\", 1.5e-2 -- ?\n/* ? */ 'unfinished")
        assertEquals(1, tokens.count { it.style == SqlTokenStyle.PARAMETER })
        assertEquals("1.5e-2", tokens.single { it.style == SqlTokenStyle.NUMBER }.text)
        assertEquals(SqlTokenStyle.STRING, tokens.last().style)
        assertEquals(2, tokens.count { it.style == SqlTokenStyle.COMMENT })
    }

    @Test fun expandedSqlSyntaxHasCompletions() {
        for ((prefix, expected) in listOf("WIN" to "WINDOW", "RAN" to "RANGE", "NTH" to "NTH_VALUE", "NTI" to "NTILE")) {
            val text = "SELECT $prefix"
            assertTrue(sqlCompletions(text, text.length, catalog).any { it.label == expected })
        }
        val text = "DELETE FROM Person p USING Pe"
        assertTrue(sqlCompletions(text, text.length, catalog).any { it.label == "Person" })
    }

    @Test fun embeddedCollectionAliasesCompleteTheirTypedFields() {
        val text = "SELECT e.value.la FROM Person p CROSS JOIN UNNEST(p.details) e"
        val completion = sqlCompletions(text, text.indexOf(" FROM"), catalog).single()
        assertEquals("label", completion.label)
        assertEquals("\"label\"", completion.insertion)
        assertTrue(completion.detail.contains("TEXT"))
    }

    @Test fun tableSuggestionsQuoteSchemaAndEscapedNames() {
        val text = "SELECT * FROM od"
        val suggestion = sqlCompletions(text, text.length, catalog).single()
        assertEquals("\"other\".\"odd\"\"table\"", suggestion.insertion)
        assertEquals("SELECT * FROM \"other\".\"odd\"\"table\"", applySqlCompletion(text, suggestion).first)
    }

    @Test fun aliasesCompleteColumnsEvenBeforeFromClause() {
        val text = "SELECT p.na FROM public.Person AS p"
        val caret = text.indexOf(" FROM")
        val suggestion = sqlCompletions(text, caret, catalog).single()
        assertEquals("name", suggestion.label)
        assertEquals("SELECT p.\"name\" FROM public.Person AS p", applySqlCompletion(text, suggestion).first)
        assertTrue(suggestion.detail.contains("TEXT"))
    }

    @Test fun completionReplacesWholeTokenWithoutChangingSurroundingSql() {
        val text = "SELECT nickxxx FROM Person"
        val suggestion = sqlCompletions(text, text.indexOf("xxx"), catalog).single()
        assertEquals("SELECT \"nickname\" FROM Person", applySqlCompletion(text, suggestion).first)
    }

    @Test fun selfJoinColumnsKeepBothSourceAliases() {
        val text = "SELECT na FROM Person a JOIN Person b ON a.__key = b.__key"
        val completions = sqlCompletions(text, text.indexOf(" FROM"), catalog)
        assertEquals(setOf("\"a\".\"name\"", "\"b\".\"name\""), completions.map { it.insertion }.toSet())
        for (alias in listOf("a", "b")) {
            val qualified = text.replace("SELECT na", "SELECT $alias.na")
            val completion = sqlCompletions(qualified, qualified.indexOf(" FROM"), catalog).single()
            assertEquals("\"name\"", completion.insertion)
        }
    }

    @Test fun noSuggestionsInsideStringsOrCommentsAndExplicitEmptyPrefixWorks() {
        for (text in listOf("SELECT 'na", "SELECT 1 -- na", "SELECT /* na")) {
            assertTrue(sqlCompletions(text, text.length, catalog, explicit = true).isEmpty())
        }
        assertTrue(sqlCompletions("SELECT ", 7, catalog, explicit = true).any { it.label == "name" })
        assertTrue(sqlCompletions("SELECT p.", 9, SqlCatalog(listOf(SqlTable("p", SqlEditorPerson)))).any { it.label == "name" })
    }
}
