package maryk.sql.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlParserSelectTest {
    @Test
    fun parsesSelectWithoutFrom() {
        val query = SqlParser("SELECT 42 AS answer").parse()

        assertEquals(1, query.select.size)
        assertEquals(SqlExpr.Literal("42", LiteralKind.NUMBER, 7), query.select.single().expression)
        assertEquals(SqlName("answer"), query.select.single().alias)
        assertNull(query.from)
        assertFalse(query.distinct)
    }

    @Test
    fun parsesImplicitProjectionAndTableAliases() {
        val query = SqlParser("SELECT u.name display_name FROM users u WHERE u.active").parse()

        assertEquals(SqlName("display_name"), query.select.single().alias)
        assertEquals(SqlName("u"), query.from?.alias)
        assertEquals(listOf(SqlName("u"), SqlName("active")), assertIs<SqlExpr.Column>(query.where).path)
    }

    @Test
    fun doesNotConsumeClauseKeywordsAsImplicitAliases() {
        val query = SqlParser("SELECT name FROM users WHERE active ORDER BY name LIMIT 1").parse()

        assertNull(query.select.single().alias)
        assertNull(query.from?.alias)
        assertEquals(listOf(SqlName("active")), assertIs<SqlExpr.Column>(query.where).path)
        assertEquals(1, query.orderBy.size)
    }

    @Test
    fun parsesEverySelectClause() {
        val query = SqlParser(
            "SELECT DISTINCT u.department AS dept, COUNT(*) AS total " +
                "FROM company.users AS u WHERE u.active = TRUE " +
                "GROUP BY u.department HAVING COUNT(*) > 1 " +
                "ORDER BY total DESC NULLS LAST, dept ASC NULLS FIRST LIMIT 10 OFFSET 2;",
        ).parse()

        assertTrue(query.distinct)
        assertEquals(listOf(SqlName("company"), SqlName("users")), query.from?.name)
        assertEquals(SqlName("u"), query.from?.alias)
        assertEquals(listOf(SqlName("u"), SqlName("department")), query.groupBy.single().columnPath())
        assertIs<SqlExpr.Binary>(query.where)
        assertIs<SqlExpr.Binary>(query.having)
        assertEquals(
            listOf(
                SqlOrder(SqlExpr.Column(listOf(SqlName("total")), 153), ascending = false, nullsFirst = false),
                SqlOrder(SqlExpr.Column(listOf(SqlName("dept")), 176), ascending = true, nullsFirst = true),
            ),
            query.orderBy,
        )
        assertEquals("10", (query.limit as SqlExpr.Literal).text)
        assertEquals("2", (query.offset as SqlExpr.Literal).text)
        assertEquals(0, query.parameterCount)
    }

    @Test
    fun normalizesFetchFirstToLimit() {
        val query = SqlParser("SELECT item FROM items OFFSET 5 FETCH FIRST 20 ROWS ONLY").parse()

        assertEquals("20", (query.limit as SqlExpr.Literal).text)
        assertEquals("5", (query.offset as SqlExpr.Literal).text)
    }

    @Test
    fun parsesExplainFlags() {
        val explained = SqlParser("EXPLAIN SELECT * FROM items").parse()
        val analyzed = SqlParser("EXPLAIN ANALYZE SELECT * FROM items").parse()

        assertTrue(explained.explain)
        assertFalse(explained.analyze)
        assertTrue(analyzed.explain)
        assertTrue(analyzed.analyze)
    }

    private fun SqlExpr.columnPath(): List<SqlName> = assertIs<SqlExpr.Column>(this).path
}
