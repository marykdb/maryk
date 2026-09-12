package maryk.sql

import kotlinx.coroutines.flow.toList
import maryk.core.models.key
import maryk.core.properties.types.Decimal
import maryk.core.query.requests.add
import maryk.datastore.memory.InMemoryDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlRelationalTest {
    private suspend fun withSql(options: SqlOptions = SqlOptions(allowTableScan = true, pageSize = 1), block: suspend (MarykSql) -> Unit) {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            for (i in 1..3) store.execute(SqlTestModel.add(SqlTestModel.create(setDefaults = false) {
                id with i.toULong()
                category with if (i < 3) "a" else "b"
            }))
            block(MarykSql.create(store, SqlCatalog(listOf(SqlTable("items", SqlTestModel))), options))
        } finally { store.close() }
    }

    @Test fun selfJoinsKeepSourceIdentityAndLeftJoinNullExtension() = sqlTest { withSql { sql ->
        val joined = sql.query("SELECT a.id, b.id FROM items a INNER JOIN items b ON a.category = b.category WHERE a.id <> b.id ORDER BY a.id")
        assertEquals(listOf(listOf(SqlValue.UInt64(1u), SqlValue.UInt64(2u)), listOf(SqlValue.UInt64(2u), SqlValue.UInt64(1u))), joined.rows.map { it.values })
        val left = sql.query("SELECT a.id, b.id FROM items a LEFT JOIN items b ON a.id = b.id AND b.id < 2 ORDER BY a.id")
        assertEquals(listOf(SqlValue.UInt64(1u), SqlValue.Null, SqlValue.Null), left.rows.map { it[1] })
        assertEquals(false, left.columns[0].nullable)
        assertEquals(true, left.columns[1].nullable)
        assertEquals(1, sql.query("SELECT a.id FROM items a LEFT JOIN items b ON a.id = b.id AND b.id < 2 WHERE b.id IS NOT NULL").rows.size)
    } }

    @Test fun valuesDerivedTablesAndReusableCtes() = sqlTest { withSql { sql ->
        val result = sql.query("WITH x(n) AS (VALUES (1), (2)), y AS (SELECT n + 1 AS m FROM x) SELECT a.n, b.m FROM x a CROSS JOIN y b ORDER BY 1, 2")
        assertEquals(4, result.rows.size)
        assertEquals(listOf(SqlValue.Int64(1), SqlValue.Exact(Decimal.parse("2"))), result.rows.first().values)
        assertEquals(listOf("n", "m"), result.columns.map { it.name })
        assertEquals(SqlValue.Int64(3), sql.query("SELECT d.column1 FROM (VALUES (3)) d").rows.single()[0])
        assertEquals(SqlValue.Int64(2), sql.query("WITH x AS (SELECT 1 AS n) SELECT n FROM (WITH x AS (SELECT 2 AS n) SELECT n FROM x) d").rows.single()[0])
    } }

    @Test fun compoundQueriesPreserveBagsNullEqualityAndTailScope() = sqlTest { withSql { sql ->
        assertEquals(listOf(SqlValue.Int64(1), SqlValue.Int64(1), SqlValue.Int64(2)), sql.query("VALUES (1), (1) UNION ALL VALUES (2) ORDER BY 1").rows.map { it[0] })
        assertEquals(1, sql.query("VALUES (NULL), (NULL) UNION VALUES (NULL)").rows.size)
        assertEquals(2, sql.query("VALUES (1), (1), (2) INTERSECT ALL VALUES (1), (1), (1)").rows.size)
        assertEquals(listOf(SqlValue.Int64(1), SqlValue.Int64(2)), sql.query("VALUES (1), (1), (2) EXCEPT ALL VALUES (1)").rows.map { it[0] })
        val exact = sql.query("VALUES (1) UNION VALUES (1.00)")
        assertEquals(SqlType.DECIMAL, exact.columns.single().type)
        assertEquals(SqlType.DECIMAL, exact.rows.single()[0].type)
        assertEquals(listOf(SqlValue.Int64(2), SqlValue.Int64(3)), sql.query("(VALUES (2), (1) ORDER BY 1 DESC LIMIT 1) UNION ALL VALUES (3) ORDER BY 1").rows.map { it[0] })
    } }

    @Test fun subqueryCardinalityAndThreeValuedIn() = sqlTest { withSql { sql ->
        val result = sql.query("SELECT (SELECT id FROM items WHERE id = 9), EXISTS (SELECT COUNT(*) FROM items WHERE id = 9), 2 IN (VALUES (1), (NULL)), 1 NOT IN (VALUES (1), (NULL)), NULL IN (SELECT id FROM items WHERE id = 9)")
        assertEquals(listOf(SqlValue.Null, SqlValue.Bool(true), SqlValue.Null, SqlValue.Bool(false), SqlValue.Bool(false)), result.rows.single().values)
        assertEquals(SqlValue.UInt64(2u), sql.query("SELECT (SELECT id FROM items WHERE id = 2)").rows.single()[0])
        assertEquals(SqlErrorCode.TYPE, assertFailsWith<SqlException> { sql.query("SELECT (VALUES (1), (2))") }.code)
    } }

    @Test fun bindingRejectsWholeInvalidRelationsBeforeReading() = sqlTest { withSql { sql ->
        for (query in listOf(
            "SELECT a.id FROM items a JOIN items b ON missing = b.id",
            "SELECT id FROM items a JOIN items b ON a.id = b.id",
            "SELECT * FROM items a CROSS JOIN items a",
            "WITH x AS (SELECT * FROM y), y AS (SELECT 1) SELECT * FROM x",
            "WITH x AS (SELECT * FROM x) SELECT * FROM x",
            "WITH x(n, m) AS (VALUES (1)) SELECT * FROM x",
            "WITH x AS (SELECT 1), x AS (SELECT 2) SELECT * FROM x",
            "SELECT d.n FROM (SELECT 1 AS n, 2 AS n) d",
            "VALUES (1) UNION VALUES (1, 2)",
            "SELECT 1 IN (VALUES (1, 2))",
            "SELECT (VALUES (1, 2))"
        )) assertEquals(SqlErrorCode.BINDING, assertFailsWith<SqlException>(query) { sql.prepare(query) }.code)
        assertEquals(SqlErrorCode.SYNTAX, assertFailsWith<SqlException> { sql.prepare("VALUES (1), (1, 2)") }.code)
        val correlated = assertFailsWith<SqlException> { sql.prepare("SELECT a.id FROM items a WHERE EXISTS (SELECT 1 FROM items b WHERE b.id = a.id)") }
        assertEquals(SqlErrorCode.UNSUPPORTED, correlated.code)
        assertTrue(correlated.message!!.contains("correlat", ignoreCase = true))
    } }

    @Test fun coercionAndGroupingUseBoundTypesAndSourceIdentity() = sqlTest { withSql { sql ->
        val result = sql.query("SELECT CASE WHEN TRUE THEN 1 ELSE 2.5 END, COALESCE(1, 2.5), ROUND(2.5), ROUND(3.5), CAST(2.345 AS DECIMAL(4, 2))")
        assertTrue(result.columns.all { it.type == SqlType.DECIMAL })
        assertTrue(result.rows.single().values.all { it.type == SqlType.DECIMAL })
        assertEquals(listOf("1", "1", "2", "4", "2.34"), result.rows.single().values.map { (it as SqlValue.Exact).value.toString() })
        assertEquals(2, sql.query("SELECT i.category, COUNT(*) FROM items i GROUP BY category").rows.size)
        assertEquals(SqlErrorCode.BINDING, assertFailsWith<SqlException> { sql.prepare("VALUES (1) UNION VALUES (CAST(1 AS DOUBLE))") }.code)
    } }

    @Test fun nestedRelationsShareFetchAndMemoryBudgetsAndZeroLimit() = sqlTest {
        withSql(SqlOptions(allowTableScan = true, pageSize = 1, maxFetchedRows = 4)) { sql ->
            val execution = sql.prepare("SELECT a.id FROM items a CROSS JOIN items b").execute()
            assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { execution.rows.toList() }.code)
            assertEquals(SqlExecutionStatus.FAILED, execution.awaitCompletion().status)
            val zero = sql.prepare("WITH x AS (SELECT * FROM items) SELECT * FROM x CROSS JOIN items LIMIT 0").execute()
            assertTrue(zero.rows.toList().isEmpty())
            assertEquals(0, zero.awaitCompletion().storeRequests)
        }
        withSql(SqlOptions(allowTableScan = true, maxBufferedBytes = 300)) { sql ->
            assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { sql.query("VALUES ('long retained value'), ('another retained value'), ('third retained value')") }.code)
        }
    }

    @Test fun nativeKeyAccessAndExactPushdownKeepResidualSemantics() = sqlTest {
        val key = SqlTestModel.key(SqlTestModel.create { id with 2uL })
        withSql(SqlOptions(allowTableScan = false, pageSize = 1)) { sql ->
            val byKey = sql.prepare("SELECT id FROM items WHERE __key IN (?, ?) AND category = 'a'")
            val execution = byKey.execute(listOf(SqlValue.Text(key.toString()), SqlValue.Text(key.toString())))
            assertEquals(listOf(SqlValue.UInt64(2u)), execution.rows.toList().map { it[0] })
            assertEquals(1, execution.awaitCompletion().storeRequests)
            assertTrue(byKey.explain().contains("Get", ignoreCase = true))
            assertEquals(SqlValue.UInt64(2u), sql.query("SELECT id FROM items WHERE id = 2").rows.single()[0])
            assertEquals(SqlErrorCode.PARAMETER, assertFailsWith<SqlException> { byKey.execute(listOf(SqlValue.Key("WrongModel", key), SqlValue.Null)) }.code)
            assertEquals(SqlErrorCode.PARAMETER, assertFailsWith<SqlException> { byKey.execute(listOf(SqlValue.Text("bad"), SqlValue.Null)) }.code)
        }
        withSql { sql ->
            assertEquals(3, sql.query("SELECT id FROM items WHERE id > -1").rows.size)
            assertEquals(3, sql.query("SELECT id FROM items WHERE id < 18446744073709551616").rows.size)
            assertEquals(1, sql.query("SELECT id FROM items WHERE id > 1.5 AND id < 3").rows.size)
            assertEquals(3, sql.query("SELECT id FROM items WHERE id = 1 OR category IS NOT NULL").rows.size)
        }
    }

    @Test fun rowDependentInQueriesAndNullHashJoins() = sqlTest { withSql { sql ->
        assertEquals(listOf(SqlValue.UInt64(2u)), sql.query("SELECT id FROM items WHERE id IN (VALUES (2), (NULL))").rows.map { it[0] })
        assertTrue(sql.query("SELECT id FROM items WHERE id NOT IN (VALUES (2), (NULL))").rows.isEmpty())
        val rows = sql.query("SELECT a.column1, b.column1 FROM (VALUES (1), (2), (NULL)) a LEFT JOIN (VALUES (1), (1), (NULL)) b ON a.column1 = b.column1 ORDER BY 1 NULLS LAST").rows
        assertEquals(4, rows.size)
        assertEquals(listOf(SqlValue.Int64(1), SqlValue.Int64(1), SqlValue.Null, SqlValue.Null), rows.map { it[1] })
        assertEquals(3, sql.query("SELECT a.column1 FROM (VALUES (1), (2)) a JOIN (VALUES (2), (3)) b ON a.column1 < b.column1").rows.size)
    } }

    @Test fun ctesAreMaterializedOnceAndUnusedRelationsDoNotRead() = sqlTest { withSql { sql ->
        val execution = sql.prepare("WITH x AS (SELECT id FROM items), unused AS (SELECT id FROM items) SELECT COUNT(*) FROM x a CROSS JOIN x b").execute()
        assertEquals(SqlValue.Int64(9), execution.rows.toList().single()[0])
        // This store finishes a full one-row cursor page with an empty continuation request.
        assertEquals(4, execution.awaitCompletion().storeRequests)
        assertEquals(3L, execution.awaitCompletion().fetchedRows)
    } }

    @Test fun parametersPropagateThroughDerivedRelationsBeforeReading() = sqlTest { withSql { sql ->
        val prepared = sql.prepare("WITH x(n) AS (VALUES (?)) SELECT n + 1 FROM x")
        assertEquals(SqlType.DECIMAL, prepared.parameters.single().type)
        assertEquals(SqlValue.Exact(Decimal.parse("3")), prepared.execute(listOf(SqlValue.Int64(2))).rows.toList().single()[0])
        assertEquals(SqlErrorCode.PARAMETER, assertFailsWith<SqlException> { prepared.execute(listOf(SqlValue.Text("wrong"))) }.code)
        val key = SqlTestModel.key(SqlTestModel.create { id with 2uL })
        val throughCte = sql.prepare("WITH wanted(k) AS (VALUES (?)) SELECT i.id FROM items i CROSS JOIN wanted w WHERE i.__key = w.k")
        assertEquals(SqlValue.UInt64(2u), throughCte.execute(listOf(SqlValue.Text(key.toString()))).rows.toList().single()[0])
        assertEquals(SqlErrorCode.PARAMETER, assertFailsWith<SqlException> { throughCte.execute(listOf(SqlValue.Key("wrong", key))) }.code)
    } }

    @Test fun quotedDerivedNamesRemainDistinct() = sqlTest { withSql { sql ->
        assertEquals(listOf(SqlValue.Int64(1), SqlValue.Int64(2)), sql.query("SELECT d.\"A\", d.a FROM (SELECT 1 AS \"A\", 2 AS a) d").rows.single().values)
        assertEquals(SqlErrorCode.BINDING, assertFailsWith<SqlException> { sql.prepare("SELECT a FROM (SELECT 1 AS \"A\") d") }.code)
    } }

    @Test fun exactRemaindersScaledProductsAndScientificFloatCasts() = sqlTest { withSql { sql ->
        val result = sql.query("SELECT 7.5 % 2, -7.5 % 2, 0.123456789012345678 * 0.1, CAST(1e20 AS DECIMAL), CAST(1e-7 AS DECIMAL)")
        assertEquals(listOf("1.5", "-1.5", "0.012345678901234568", "100000000000000000000", "0.0000001"), result.rows.single().values.map { (it as SqlValue.Exact).value.toString() })
    } }

    @Test fun planDepthAndRetainedSubqueriesHaveExplicitLimits() = sqlTest {
        withSql { sql ->
            val chain = (0..70).joinToString(", ") { if (it == 0) "x0 AS (SELECT 1 AS n)" else "x$it AS (SELECT n FROM x${it - 1})" }
            assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { sql.prepare("WITH $chain SELECT * FROM x70") }.code)
        }
        withSql(SqlOptions(allowTableScan = true, maxBufferedBytes = 600)) { sql ->
            assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { sql.query("WITH x AS (VALUES ('one'), ('two'), ('three')) SELECT * FROM x") }.code)
        }
    }

    @Test fun constantPreflightPreservesShortCircuitBranches() = sqlTest { withSql { sql ->
        assertEquals(listOf(SqlValue.Exact(Decimal.parse("1")), SqlValue.Exact(Decimal.parse("1")), SqlValue.Bool(true)),
            sql.query("SELECT CASE WHEN TRUE THEN 1 ELSE 1 / 0 END, COALESCE(1, 1 / 0), TRUE OR (1 / 0 = 0)").rows.single().values)
        val empty = sql.prepare("SELECT id FROM items WHERE FALSE AND 1 / 0 = 0").execute()
        assertTrue(empty.rows.toList().isEmpty())
        assertEquals(0, empty.awaitCompletion().storeRequests)
    } }

    @Test fun likeScratchBuffersAndParserDepthUseLimitErrors() = sqlTest {
        withSql(SqlOptions(allowTableScan = true, maxBufferedBytes = 500)) { sql ->
            assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { sql.query("SELECT '${"a".repeat(100)}' LIKE '%'") }.code)
        }
        withSql { sql ->
            assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { sql.prepare("SELECT ${"(".repeat(100)}1${")".repeat(100)}") }.code)
        }
    }
}
