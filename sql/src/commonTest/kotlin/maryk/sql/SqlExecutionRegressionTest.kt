package maryk.sql

import kotlinx.coroutines.flow.toList
import maryk.datastore.memory.InMemoryDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqlExecutionRegressionTest {
    private suspend fun withSql(block: suspend (MarykSql) -> Unit) {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try { block(MarykSql.create(store, options = SqlOptions(allowTableScan = true))) }
        finally { store.close() }
    }

    @Test fun existsPreservesDistinctAcrossNestedOffsets() = sqlTest { withSql { sql ->
        val cases = mapOf(
            "(SELECT DISTINCT column1 FROM (VALUES (1), (1)) t) OFFSET 1" to false,
            "(SELECT DISTINCT column1 FROM (VALUES (1), (2)) t) OFFSET 1" to true,
            "((SELECT DISTINCT column1 FROM (VALUES (1), (1)) t)) OFFSET 1" to false,
            "SELECT DISTINCT column1 FROM (VALUES (1), (1)) t UNION ALL SELECT 2 OFFSET 2" to false,
            "SELECT DISTINCT column1 FROM (VALUES (1), (1)) t UNION ALL SELECT 2 OFFSET 1" to true,
            "SELECT 1 / 0 UNION ALL SELECT 2 / 0 OFFSET 1" to true,
        )
        for ((query, expected) in cases) {
            assertEquals(SqlValue.Bool(expected), sql.query("SELECT EXISTS ($query)").rows.single()[0], query)
        }
    } }

    @Test fun existsOmitsUnusedProjectionAndOrderingInputs() = sqlTest { withSql { sql ->
        sql.execute("INSERT INTO SqlTestModel (id, note) VALUES (1, ?)", listOf(SqlValue.Text("x".repeat(5000))))
        val bounded = MarykSql.create(sql.dataStore, options = SqlOptions(allowTableScan = true, pageSize = 1, maxBufferedBytes = 256))
        for (inner in listOf("SELECT 1 FROM SqlTestModel", "SELECT note FROM SqlTestModel", "SELECT 1 FROM SqlTestModel ORDER BY note")) {
            assertEquals(SqlValue.Bool(true), bounded.query("SELECT EXISTS ($inner)").rows.single()[0], inner)
        }
        // Predicate values remain live even when the selected value is discarded.
        assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> {
            bounded.query("SELECT EXISTS (SELECT 1 FROM SqlTestModel WHERE LENGTH(note) > 0)")
        }.code)
    } }

    @Test fun nativeAccessPreservesLazyConjunctions() = sqlTest { withSql { sql ->
        assertEquals(1, sql.execute("INSERT INTO SqlTestModel (id) VALUES (1)").affectedRows)
        assertEquals(1, sql.query("SELECT id FROM SqlTestModel").rows.size)
        assertEquals(
            sql.query("SELECT id FROM SqlTestModel WHERE id + 0 > 0").rows.map { it[0] },
            sql.query("SELECT id FROM SqlTestModel WHERE id > 0").rows.map { it[0] },
        )
        for (predicate in listOf("FALSE AND id = 1 / 0", "id > 10 AND id = 1 / 0", "(id > 10 AND id < 20) AND id = ABS(1 / 0)")) {
            assertEquals(emptyList(), sql.query("SELECT id FROM SqlTestModel WHERE $predicate").rows, predicate)
        }
        assertEquals(SqlErrorCode.ARITHMETIC, assertFailsWith<SqlException> {
            sql.query("SELECT id FROM SqlTestModel WHERE id > 0 AND id = 1 / 0")
        }.code)
        assertEquals(1, sql.query("SELECT id FROM SqlTestModel WHERE TRUE AND id = 1").rows.size)
    } }

    @Test fun floatingOverflowHasAnArithmeticErrorCategory() = sqlTest { withSql { sql ->
        for (statement in listOf("SELECT 1e308 * 1e308", "SELECT 1e308 + 1e308", "SELECT SUM(column1) FROM (VALUES (1e308), (1e308)) t")) {
            assertEquals(SqlErrorCode.ARITHMETIC, assertFailsWith<SqlException> { sql.query(statement) }.code, statement)
        }
        assertEquals(SqlValue.Float64(1e308), sql.query("SELECT 1e308 * 1.0e0").rows.single()[0])
        assertEquals(SqlValue.Float64(1.0), sql.query("SELECT CASE WHEN TRUE THEN 1.0e0 ELSE 1e308 * 1e308 END").rows.single()[0])
    } }

    @Test fun zeroDivisionAndRemainderUseArithmeticErrorsAtEveryScale() = sqlTest { withSql { sql ->
        for (operator in listOf("/", "%")) for (zero in listOf("0", "0.0", "0.00", "-0.00")) {
            assertEquals(SqlErrorCode.ARITHMETIC, assertFailsWith<SqlException> {
                sql.query("SELECT 1 $operator $zero")
            }.code, "1 $operator $zero")
        }
    } }

    @Test fun nativeKeyInKeepsLaterCandidatesLazy() = sqlTest { withSql { sql ->
        sql.execute("INSERT INTO SqlTestModel (id) VALUES (1)")
        val key = sql.query("SELECT __key FROM SqlTestModel").rows.single()[0]
        val statement = "SELECT id FROM SqlTestModel WHERE __key IN (?, CASE WHEN 1 / 0 = 0 THEN ? ELSE NULL END)"
        assertEquals(SqlValue.UInt64(1u), sql.query(statement, listOf(key, key)).rows.single()[0])
        assertEquals(SqlErrorCode.ARITHMETIC, assertFailsWith<SqlException> {
            sql.query(statement, listOf(SqlValue.Null, key))
        }.code)
        val indexed = MarykSql.create(sql.dataStore)
        assertEquals(SqlValue.UInt64(1u), indexed.query("SELECT id FROM SqlTestModel WHERE __key IN (?, ?)", listOf(key, SqlValue.Null)).rows.single()[0])
    } }

    @Test fun nativeFiltersDoNotHideEarlierArithmeticErrors() = sqlTest { withSql { sql ->
        sql.execute("INSERT INTO SqlTestModel (id) VALUES (1)")
        assertEquals(SqlErrorCode.ARITHMETIC, assertFailsWith<SqlException> {
            sql.query("SELECT id FROM SqlTestModel WHERE id / 0 > 0 AND id = 99")
        }.code)
    } }

    @Test fun hashJoinsPreservePredicateErrorOrder() = sqlTest { withSql { sql ->
        sql.execute("INSERT INTO SqlTestModel (id) VALUES (1)")
        for (join in listOf("JOIN", "LEFT JOIN")) {
            assertEquals(SqlErrorCode.ARITHMETIC, assertFailsWith<SqlException> {
                sql.query("SELECT a.id FROM SqlTestModel a $join (VALUES (2)) b ON a.id / 0 > 0 AND a.id = b.column1")
            }.code, join)
        }
        assertEquals(emptyList(), sql.query("SELECT a.id FROM SqlTestModel a JOIN (VALUES (2)) b ON a.id = b.column1 AND a.id / 0 > 0").rows)
        assertEquals(SqlErrorCode.ARITHMETIC, assertFailsWith<SqlException> {
            sql.query("SELECT a.id FROM SqlTestModel a JOIN (VALUES (1)) b ON a.id = b.column1 AND a.id / 0 > 0")
        }.code)
    } }

    @Test fun numericCategoryReachesAggregateParameters() = sqlTest { withSql { sql ->
        for (aggregate in listOf("SUM", "AVG", "MIN", "MAX")) {
            val prepared = sql.prepare("SELECT $aggregate(?) + 1.0e0")
            assertEquals(SqlType.FLOAT64, prepared.parameters.single().type, aggregate)
            assertEquals(SqlValue.Float64(3.0), prepared.execute(listOf(SqlValue.Float64(2.0))).rows.toList().single()[0])
            assertEquals(SqlErrorCode.PARAMETER, assertFailsWith<SqlException> {
                prepared.execute(listOf(SqlValue.Int64(2)))
            }.code)
        }
        assertEquals(SqlType.ANY, sql.prepare("SELECT SUM(?)").parameters.single().type)
        assertEquals(SqlValue.Text("text"), sql.query("SELECT MIN(?)", listOf(SqlValue.Text("text"))).rows.single()[0])
    } }
}
