package maryk.sql.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlParserExpressionTest {
    @Test
    fun appliesSqlOperatorPrecedence() {
        val expression = SqlParser("SELECT 1 + 2 * 3 || 'x' = '7x' OR NOT FALSE AND TRUE").parse().select.single().expression

        val or = expression.binary("OR")
        val equality = or.left.binary("=")
        val concatenation = equality.left.binary("||")
        val addition = concatenation.left.binary("+")
        assertEquals("1", addition.left.literal().text)
        assertEquals("*", assertIs<SqlExpr.Binary>(addition.right).operator)
        assertEquals("x", concatenation.right.literal().text)
        val and = or.right.binary("AND")
        assertEquals("NOT", assertIs<SqlExpr.Unary>(and.left).operator)
    }

    @Test
    fun parsesRemainderAtMultiplicativePrecedence() {
        val expression = SqlParser("SELECT 17 % 5 * 2 + 1").parse().select.single().expression

        val addition = expression.binary("+")
        val multiplication = addition.left.binary("*")
        val remainder = multiplication.left.binary("%")
        assertEquals("17", remainder.left.literal().text)
        assertEquals("5", remainder.right.literal().text)
        assertEquals("2", multiplication.right.literal().text)
    }

    @Test
    fun parsesPredicateForms() {
        val query = SqlParser(
            "SELECT " +
                "a IS NOT NULL, " +
                "b IS DISTINCT FROM c, " +
                "d NOT IN (1, 2), " +
                "e BETWEEN 3 AND 4, " +
                "f NOT LIKE 'x!_%' ESCAPE '!'",
        ).parse()

        assertEquals(SqlExpr.Is(SqlExpr.Column(listOf(SqlName("a")), 7), "NULL", true, 7), query.select[0].expression)
        assertEquals("IS DISTINCT FROM", assertIs<SqlExpr.Binary>(query.select[1].expression).operator)
        assertTrue(assertIs<SqlExpr.In>(query.select[2].expression).negated)
        assertEquals(2, assertIs<SqlExpr.In>(query.select[2].expression).values.size)
        assertEquals("4", assertIs<SqlExpr.Between>(query.select[3].expression).upper.literal().text)
        val like = assertIs<SqlExpr.Like>(query.select[4].expression)
        assertTrue(like.negated)
        assertEquals("!", like.escape?.literal()?.text)
    }

    @Test
    fun parsesCallsCastAndAggregateFilter() {
        val query = SqlParser(
            "SELECT COUNT(DISTINCT score) FILTER (WHERE active), " +
                "COUNT(*), CAST(amount AS DECIMAL(12, 2))",
        ).parse()

        val filtered = assertIs<SqlExpr.Call>(query.select[0].expression)
        assertEquals("count", filtered.name.normalized)
        assertTrue(filtered.distinct)
        assertIs<SqlExpr.Column>(filtered.filter)
        assertIs<SqlExpr.Star>(assertIs<SqlExpr.Call>(query.select[1].expression).arguments.single())
        val cast = assertIs<SqlExpr.Cast>(query.select[2].expression)
        assertEquals(SqlCastType("DECIMAL", 12, 2), cast.type)
    }

    @Test
    fun parsesSearchedAndSimpleCase() {
        val query = SqlParser(
            "SELECT CASE WHEN ready THEN 'yes' ELSE 'no' END, " +
                "CASE status WHEN 1 THEN 'new' WHEN 2 THEN 'done' END",
        ).parse()

        val searched = assertIs<SqlExpr.Case>(query.select[0].expression)
        assertNull(searched.operand)
        assertEquals(1, searched.branches.size)
        assertEquals("no", searched.otherwise?.literal()?.text)

        val simple = assertIs<SqlExpr.Case>(query.select[1].expression)
        assertIs<SqlExpr.Column>(simple.operand)
        assertEquals(2, simple.branches.size)
        assertNull(simple.otherwise)
    }

    @Test
    fun retainsLiteralTextAndAssignsParameterIndexes() {
        val query = SqlParser(
            "SELECT 001.20e+03, 'it''s', NULL, TRUE, DATE '2026-09-12', " +
                "TIME '10:20:30', TIMESTAMP '2026-09-12 10:20:30' WHERE ? = ?",
        ).parse()

        assertEquals("001.20e+03", query.select[0].expression.literal().text)
        assertEquals("it's", query.select[1].expression.literal().text)
        assertEquals(
            listOf(LiteralKind.NUMBER, LiteralKind.STRING, LiteralKind.NULL, LiteralKind.BOOLEAN, LiteralKind.DATE, LiteralKind.TIME, LiteralKind.TIMESTAMP),
            query.select.map { it.expression.literal().kind },
        )
        val comparison = assertIs<SqlExpr.Binary>(query.where)
        assertEquals(0, assertIs<SqlExpr.Parameter>(comparison.left).index)
        assertEquals(1, assertIs<SqlExpr.Parameter>(comparison.right).index)
        assertEquals(2, query.parameterCount)
    }

    private fun SqlExpr.binary(operator: String): SqlExpr.Binary =
        assertIs<SqlExpr.Binary>(this).also { assertEquals(operator, it.operator) }

    private fun SqlExpr.literal(): SqlExpr.Literal = assertIs<SqlExpr.Literal>(this)
}
