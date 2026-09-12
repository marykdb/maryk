package maryk.sql.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SqlParserLexingTest {
    @Test
    fun foldsOnlyAsciiInUnquotedNames() {
        val query = SqlParser("SELECT MiXeD, \"MiXeD\", ÅNGSTRÖM FROM \"My.Schema\" AS T").parse()

        val unquoted = assertIs<SqlExpr.Column>(query.select[0].expression).path.single()
        val quoted = assertIs<SqlExpr.Column>(query.select[1].expression).path.single()
        val nonAscii = assertIs<SqlExpr.Column>(query.select[2].expression).path.single()
        assertEquals("mixed", unquoted.normalized)
        assertFalse(unquoted.quoted)
        assertEquals("MiXeD", quoted.normalized)
        assertTrue(quoted.quoted)
        assertEquals("ÅngstrÖm", nonAscii.normalized)
        assertEquals("My.Schema", query.from?.name?.single()?.text)
    }

    @Test
    fun decodesDoubledQuotedIdentifierDelimiters() {
        val expression = SqlParser("SELECT \"a\"\"b\"").parse().select.single().expression

        assertEquals(SqlName("a\"b", quoted = true), assertIs<SqlExpr.Column>(expression).path.single())
    }

    @Test
    fun skipsLineAndBlockComments() {
        val query = SqlParser(
            "-- heading\nSELECT a /* projection */ FROM items -- source\nWHERE a = 1",
        ).parse()

        assertEquals("items", query.from?.name?.single()?.normalized)
        assertIs<SqlExpr.Binary>(query.where)
    }

    @Test
    fun parsesQualifiedColumnsAndStars() {
        val query = SqlParser("SELECT schema.table.value, table.*, * FROM schema.table").parse()

        assertEquals(listOf("schema", "table", "value"), assertIs<SqlExpr.Column>(query.select[0].expression).path.map { it.normalized })
        assertEquals(SqlName("table"), assertIs<SqlExpr.Star>(query.select[1].expression).qualifier)
        assertEquals(null, assertIs<SqlExpr.Star>(query.select[2].expression).qualifier)
    }
}
