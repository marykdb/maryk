package maryk.sql

import maryk.datastore.memory.InMemoryDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqlBranchRegressionTest {
    private suspend fun withSql(block: suspend (MarykSql) -> Unit) {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try { block(MarykSql.create(store, options = SqlOptions(allowTableScan = true))) }
        finally { store.close() }
    }

    @Test fun expectedTypesReachConditionalParametersBeforeReads() = sqlTest { withSql { sql ->
        for (statement in listOf(
            "SELECT id FROM SqlTestModel WHERE COALESCE(?, NULL)",
            "SELECT id FROM SqlTestModel WHERE CASE WHEN id > 0 THEN ? ELSE NULL END",
            "SELECT id FROM SqlTestModel WHERE (SELECT ?) IS TRUE",
        )) {
            val prepared = sql.prepare(statement)
            assertEquals(SqlType.BOOLEAN, prepared.parameters.single().type)
            assertEquals(SqlErrorCode.PARAMETER, assertFailsWith<SqlException> {
                prepared.execute(listOf(SqlValue.Text("wrong")))
            }.code)
        }
    } }

    @Test fun unusedSubqueryBranchesRemainLazy() = sqlTest { withSql { sql ->
        sql.execute("INSERT INTO SqlTestModel (id) VALUES (1)")
        for (statement in listOf(
            "SELECT COALESCE(1, (SELECT 1 / 0))",
            "SELECT CASE WHEN id > 0 THEN 1 ELSE (SELECT 1 / 0) END FROM SqlTestModel",
        )) assertEquals("1", sql.query(statement).rows.single()[0].display())
        assertEquals(SqlValue.Bool(true), sql.query("SELECT TRUE OR (SELECT 1 / 0) = 0").rows.single()[0])
        assertEquals(SqlErrorCode.ARITHMETIC, assertFailsWith<SqlException> {
            sql.query("SELECT COALESCE(NULL, (SELECT 1 / 0))")
        }.code)
    } }

    @Test fun roundedLargeFloatsStayFinite() = sqlTest { withSql { sql ->
        for (value in listOf(1e308, -1e308)) {
            assertEquals(SqlValue.Float64(value), sql.query("SELECT ROUND(?, 2)", listOf(SqlValue.Float64(value))).rows.single()[0])
        }
        assertEquals(SqlValue.Float64(1.25), sql.query("SELECT ROUND(1.246e0, 2)").rows.single()[0])
    } }

    @Test fun parenthesizedSubqueriesComposeWithPredicates() = sqlTest { withSql { sql ->
        for (statement in listOf(
            "SELECT EXISTS ((SELECT 1))",
            "SELECT 1 IN ((SELECT 1) UNION ALL (SELECT 2))",
            "SELECT ((SELECT 1) UNION ALL (SELECT 2) LIMIT 1) = 1",
            "SELECT (1 + (2 * 3)) = 7",
            "SELECT ((SELECT 1) + 2) = 3",
            "SELECT 2 IN ((SELECT 1), 2)",
        )) assertEquals(SqlValue.Bool(true), sql.query(statement).rows.single()[0], statement)
    } }
}
