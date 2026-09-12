package maryk.sql

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import maryk.core.properties.types.Decimal
import maryk.core.query.requests.add
import maryk.datastore.memory.InMemoryDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqlQueryTest {
    @Test
    fun expressionsAndNullSemanticsWithoutStoreReads() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store)
            val result = sql.query("SELECT ? + 2 AS n, NULL = NULL AS unknown, FALSE AND NULL AS f, CASE WHEN ? IS NULL THEN 'yes' ELSE 'no' END AS answer", listOf(SqlValue.Int64(3), SqlValue.Null))
            assertEquals(listOf("n", "unknown", "f", "answer"), result.columns.map { it.name })
            assertEquals(listOf(SqlValue.Exact(Decimal.parse("5")), SqlValue.Null, SqlValue.Bool(false), SqlValue.Text("yes")), result.rows.single().values)
            assertEquals("0.333333", (sql.query("SELECT 1 / 3").rows.single()[0] as SqlValue.Exact).value.toString())
        } finally { store.close() }
    }

    @Test
    fun filtersSortOffsetAndAggregatesUseEveryPage() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            for (i in 1..7) store.execute(SqlTestModel.add(SqlTestModel.create(setDefaults = false) {
                id with i.toULong()
                category with if (i % 2 == 0) "even" else "odd"
                amount with Decimal.parse("$i.00")
            }))
            val sql = MarykSql.create(store, SqlCatalog(listOf(SqlTable("items", SqlTestModel))), SqlOptions(allowTableScan = true, pageSize = 2))
            assertEquals(listOf(6uL, 5uL), sql.query("SELECT id FROM items WHERE amount > 2 ORDER BY id DESC LIMIT 2 OFFSET 1").rows.map { (it[0] as SqlValue.UInt64).value })
            val groups = sql.query("SELECT category, COUNT(*) AS n, SUM(amount) AS total, AVG(amount) AS average FROM items GROUP BY category HAVING COUNT(*) > 2 ORDER BY category")
            assertEquals(listOf("even", "odd"), groups.rows.map { (it[0] as SqlValue.Text).value })
            assertEquals(listOf(3L, 4L), groups.rows.map { (it[1] as SqlValue.Int64).value })
            assertEquals(listOf("12.00", "16.00"), groups.rows.map { (it[2] as SqlValue.Exact).value.toString() })
            assertEquals(listOf("4.000000", "4.000000"), groups.rows.map { (it[3] as SqlValue.Exact).value.toString() })
            assertEquals(2, sql.query("SELECT DISTINCT category FROM items").rows.size)
            assertEquals(SqlValue.Null, sql.query("SELECT note FROM items LIMIT 1").rows.single()[0])
            assertEquals(SqlValue.Int64(0), sql.query("SELECT COUNT(*) FROM items WHERE id > 20").rows.single()[0])
            assertEquals(0, sql.query("SELECT category FROM items LIMIT 0").rows.size)
        } finally { store.close() }
    }

    @Test
    fun invalidSqlAndUngroupedColumnsFail() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store, SqlCatalog(listOf(SqlTable("items", SqlTestModel))))
            assertFailsWith<SqlException> { sql.query("DELETE FROM items") }
            assertFailsWith<SqlException> { sql.query("SELECT missing FROM items") }
            assertFailsWith<SqlException> { sql.query("SELECT category, COUNT(*) FROM items") }
            assertFailsWith<SqlException> { sql.query("SELECT 1 WHERE 3") }
            assertFailsWith<SqlException> { sql.query("SELECT ?", emptyList()) }
        } finally { store.close() }
    }
}

internal fun sqlTest(block: suspend () -> Unit) = runTest { withContext(Dispatchers.Default) { block() } }
