package io.maryk.app.ui.sql

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.sql.SqlCatalog
import maryk.sql.SqlTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object SqlEditorPerson : RootDataModel<SqlEditorPerson>() {
    val name by string(1u)
    val nickname by string(2u, required = false)
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
