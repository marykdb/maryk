package maryk.sql

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import maryk.core.properties.types.Decimal
import maryk.datastore.memory.InMemoryDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqlWriteTest {
    @Test
    fun insertUpdateAndDeleteUseStoreRequests() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store, SqlCatalog(listOf(SqlTable("items", SqlTestModel))), SqlOptions(allowTableScan = true))
            assertEquals(2, sql.execute(
                "INSERT INTO items (id, category, amount) VALUES (?, ?, ?), (?, ?, ?)",
                listOf(SqlValue.UInt64(8u), SqlValue.Text("new"), SqlValue.Exact(Decimal.parse("12.50")), SqlValue.UInt64(9u), SqlValue.Text("other"), SqlValue.Exact(Decimal.parse("9.00"))),
            ).affectedRows)
            assertEquals("new", (sql.query("SELECT category FROM items WHERE id = 8").rows.single()[0] as SqlValue.Text).value)

            val key = sql.query("SELECT __key FROM items WHERE id = 8").rows.single()[0]
            assertEquals(1, sql.execute("UPDATE items SET category = 'changed', amount = amount + 0.75 WHERE __key = ?", listOf(key)).affectedRows)
            val row = sql.query("SELECT category, amount FROM items WHERE id = 8").rows.single()
            assertEquals(SqlValue.Text("changed"), row[0])
            assertEquals(Decimal.parse("13.25"), (row[1] as SqlValue.Exact).value)

            val version = sql.query("SELECT __version FROM items WHERE id = 8").rows.single()[0]
            assertEquals(1, sql.query("SELECT __key FROM items WHERE __key = ? AND __version = ?", listOf(key, version)).rows.size)
            assertEquals(1, sql.execute("UPDATE items SET category = 'versioned' WHERE __key = ? AND __version = ?", listOf(key, version)).affectedRows)
            assertEquals(0, sql.execute("UPDATE items SET category = 'stale' WHERE __version = ? AND __key = ?", listOf(version, key)).affectedRows)
            val currentVersion = sql.query("SELECT __version FROM items WHERE id = 8").rows.single()[0]
            assertEquals(0, sql.execute("DELETE FROM items WHERE __key = ? AND __version = ?", listOf(key, version)).affectedRows)
            assertEquals(1, sql.execute("DELETE FROM items WHERE __key = ? AND __version = ?", listOf(key, currentVersion)).affectedRows)
            assertEquals(0, sql.query("SELECT id FROM items WHERE id = 8").rows.size)
        } finally { store.close() }
    }

    @Test
    fun writesRequireTheBoundedFirstVersionSurface() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store, SqlCatalog(listOf(SqlTable("items", SqlTestModel))))
            assertFailsWith<SqlException> { sql.execute("UPDATE items SET category = 'changed' WHERE id = 8") }
            assertFailsWith<SqlException> { sql.execute("DELETE FROM items") }
        } finally { store.close() }
    }

    @Test
    fun writesSupportQuotedSchemaAndTableNames() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store, SqlCatalog(listOf(SqlTable("order items", SqlTestModel, "custom schema"))), SqlOptions(allowTableScan = true))
            assertEquals(1, sql.execute("INSERT INTO \"custom schema\".\"order items\" (id, category) VALUES (10, 'new')").affectedRows)
            val key = sql.query("SELECT __key FROM \"custom schema\".\"order items\" WHERE id = 10").rows.single()[0]
            assertEquals(1, sql.execute("UPDATE \"custom schema\".\"order items\" SET category = 'changed' WHERE __key = ?", listOf(key)).affectedRows)
            val deleted = sql.execute("DELETE FROM \"custom schema\".\"order items\" WHERE __key = ?", listOf(key))
            assertEquals(1, deleted.affectedRows, deleted.failures.joinToString())
        } finally { store.close() }
    }

    @Test
    fun writesApplyTheReadStatementLimits() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store, SqlCatalog(listOf(SqlTable("items", SqlTestModel))))
            assertFailsWith<SqlException> { sql.execute("INSERT" + " ".repeat(65 * 1024)) }
            assertFailsWith<SqlException> { sql.execute("INSERT INTO items (id) VALUES " + List(1_025) { "(?)" }.joinToString()) }
        } finally { store.close() }
    }
}
