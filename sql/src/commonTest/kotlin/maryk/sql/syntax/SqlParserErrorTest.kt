package maryk.sql.syntax

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlParserErrorTest {
    @Test
    fun rejectsWritesUnsupportedClausesAndTrailingStatements() {
        listOf(
            "INSERT INTO users VALUES (1)",
            "UPDATE users SET active = TRUE",
            "DELETE FROM users",
            "SELECT 1; SELECT 2",
            "SELECT 1;;",
        ).forEach { sql ->
            assertFailsWith<SqlParseException>(sql) { SqlParser(sql).parse() }
        }
    }

    @Test
    fun rejectsMalformedExpressionsAndClauses() {
        listOf(
            "SELECT",
            "SELECT a,",
            "SELECT a IN ()",
            "SELECT a BETWEEN 1",
            "SELECT CASE WHEN a THEN 1",
            "SELECT CAST(a AS DECIMAL(2, 3, 4))",
            "SELECT 1abc",
            "SELECT 1.2value",
            "SELECT 1e2value",
            "SELECT .5value",
            "SELECT 'unterminated",
            "SELECT a /* unterminated",
            "SELECT 1 FROM",
            "SELECT 1 LIMIT -",
        ).forEach { sql ->
            assertFailsWith<SqlParseException>(sql) { SqlParser(sql).parse() }
        }
    }

    @Test
    fun reportsSourcePosition() {
        val error = assertFailsWith<SqlParseException> { SqlParser("SELECT 1 + )").parse() }

        assertEquals(11, error.position)
        assertContains(error.message.orEmpty(), "position")
    }

    @Test
    fun enforcesTextTokenParameterAndDepthBudgets() {
        assertFailsWith<SqlParseException> { SqlParser("SELECT ${"x".repeat(65 * 1024)}").parse() }
        assertFailsWith<SqlParseException> { SqlParser("SELECT 1 + 2 + 3", maxTokens = 5).parse() }
        assertFailsWith<SqlParseException> { SqlParser("SELECT ?, ?", maxParameters = 1).parse() }
        assertFailsWith<SqlParseException> { SqlParser("SELECT (((1)))", maxDepth = 2).parse() }
        assertFailsWith<SqlParseException> { SqlParser("SELECT 1 + 2 + 3", maxDepth = 2).parse() }
    }

    @Test
    fun rejectsUnpairedSurrogates() {
        val high = 0xD800.toChar().toString()
        val low = 0xDC00.toChar().toString()

        // Kotlin/JS normalizes an unpaired UTF-16 surrogate before the lexer receives it.
        if (high[0].code == 0xD800) assertFailsWith<SqlParseException> { SqlParser("SELECT '$high'").parse() }
        if (low[0].code == 0xDC00) assertFailsWith<SqlParseException> { SqlParser("SELECT '$low'").parse() }
        assertTrue(SqlParser("SELECT '${high + low}'").parse().select.isNotEmpty())
    }
}
