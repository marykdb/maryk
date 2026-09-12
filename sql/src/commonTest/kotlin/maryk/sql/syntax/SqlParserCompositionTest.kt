package maryk.sql.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlParserCompositionTest {
    @Test
    fun parsesNonRecursiveCommonTableExpressions() {
        val query = SqlParser(
            "WITH active_users(id, name) AS (SELECT id, name FROM users WHERE active), " +
                "counts AS (SELECT COUNT(*) AS total FROM active_users) " +
                "SELECT total FROM counts",
        ).parse()

        assertEquals(listOf("active_users", "counts"), query.ctes.map { it.name.normalized })
        assertEquals(listOf("id", "name"), query.ctes[0].columns.map { it.normalized })
        assertIs<SqlExpr.Column>(query.ctes[0].query.where)
        assertEquals("counts", query.from?.name?.single()?.normalized)
    }

    @Test
    fun parsesDerivedTablesAndBoundedJoins() {
        val query = SqlParser(
            "SELECT u.id FROM (SELECT id FROM users) AS u " +
                "INNER JOIN profiles AS p ON u.id = p.user_id " +
                "LEFT JOIN flags f ON f.user_id = u.id " +
                "CROSS JOIN regions r",
        ).parse()

        val source = requireNotNull(query.from)
        assertTrue(source.name.isEmpty())
        assertEquals(SqlName("u"), source.alias)
        assertEquals("users", source.derived?.from?.name?.single()?.normalized)
        assertEquals(listOf(SqlJoinType.INNER, SqlJoinType.LEFT, SqlJoinType.CROSS), source.joins.map { it.type })
        assertIs<SqlExpr.Binary>(source.joins[0].condition)
        assertIs<SqlExpr.Binary>(source.joins[1].condition)
        assertNull(source.joins[2].condition)
    }

    @Test
    fun appliesIntersectPrecedenceAndCompoundTail() {
        val query = SqlParser(
            "SELECT a FROM first_set UNION ALL SELECT a FROM second_set " +
                "INTERSECT SELECT a FROM third_set EXCEPT SELECT a FROM fourth_set " +
                "ORDER BY a DESC LIMIT 4",
        ).parse()

        assertTrue(query.select.isEmpty())
        assertEquals(SqlSetOperator.EXCEPT, query.setOperation?.operator)
        val union = requireNotNull(query.setOperation?.left?.setOperation)
        assertEquals(SqlSetOperator.UNION, union.operator)
        assertTrue(union.all)
        assertEquals(SqlSetOperator.INTERSECT, union.right.setOperation?.operator)
        assertFalse(union.right.setOperation?.all ?: true)
        assertEquals(1, query.orderBy.size)
        assertEquals("4", assertIs<SqlExpr.Literal>(query.limit).text)
    }

    @Test
    fun parsesValuesRelations() {
        val query = SqlParser("VALUES (1, 'a'), (2, 'b') ORDER BY 1").parse()

        assertTrue(query.select.isEmpty())
        assertEquals(2, query.values?.size)
        assertEquals("b", assertIs<SqlExpr.Literal>(query.values?.get(1)?.get(1)).text)
        assertEquals(1, query.orderBy.size)
    }

    @Test
    fun parsesScalarExistsAndInSubqueries() {
        val query = SqlParser(
            "SELECT (SELECT MAX(score) FROM scores) AS maximum " +
                "FROM users u WHERE EXISTS (SELECT 1 FROM flags f WHERE f.user_id = u.id) " +
                "AND u.id NOT IN (SELECT banned_id FROM bans)",
        ).parse()

        assertIs<SqlExpr.Subquery>(query.select.single().expression)
        val and = assertIs<SqlExpr.Binary>(query.where)
        assertFalse(assertIs<SqlExpr.Exists>(and.left).negated)
        assertTrue(assertIs<SqlExpr.InQuery>(and.right).negated)
    }

    @Test
    fun rejectsRecursiveWithAndInvalidJoinForms() {
        listOf(
            "WITH RECURSIVE ids AS (SELECT 1) SELECT * FROM ids",
            "SELECT * FROM a RIGHT JOIN b ON a.id = b.id",
            "SELECT * FROM a FULL JOIN b ON a.id = b.id",
            "SELECT * FROM a INNER JOIN b",
            "SELECT * FROM a CROSS JOIN b ON TRUE",
            "SELECT * FROM (SELECT 1)",
        ).forEach { sql ->
            assertFailsWith<SqlParseException>(sql) { SqlParser(sql).parse() }
        }
    }
}
