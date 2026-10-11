package maryk.sql

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.delay
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Decimal
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.TransactionalDataStore
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.responses.TransactionResponse
import maryk.datastore.shared.IsDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlReviewRegressionTest {
    private suspend fun withSql(options: SqlOptions = SqlOptions(allowTableScan = true), block: suspend (MarykSql) -> Unit) {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try { block(MarykSql.create(store, SqlCatalog(listOf(SqlTable("items", SqlTestModel))), options)) }
        finally { store.close() }
    }

    @Test fun parenthesizedQueryTailsKeepTheirScope() = sqlTest { withSql { sql ->
        assertEquals(listOf(SqlValue.Int64(2)), sql.query("(VALUES (1), (2) ORDER BY 1 DESC LIMIT 1)").rows.map { it[0] })
        assertEquals(listOf(SqlValue.Int64(2)), sql.query("(VALUES (3), (2), (1) ORDER BY 1 LIMIT 2) ORDER BY 1 DESC LIMIT 1").rows.map { it[0] })
        assertEquals(SqlValue.Int64(2), sql.query("SELECT (SELECT column1 FROM ((VALUES (1), (2) ORDER BY 1 DESC LIMIT 1)) t)").rows.single()[0])
    } }

    @Test fun unaryChainsFailBeforeExhaustingThePlatformStack() = sqlTest { withSql { sql ->
        for (prefix in listOf("+ ", "NOT ")) {
            assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { sql.prepare("SELECT " + prefix.repeat(4000) + "TRUE") }.code)
        }
        assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { sql.prepare("SELECT '" + "a".repeat(65536) + "'") }.code)
    } }

    @Test fun malformedMutationsCannotCommit() = sqlTest { withSql { sql ->
        sql.execute("INSERT INTO items (id, category) VALUES (1, 'original')")
        val key = sql.query("SELECT __key FROM items").rows.single()[0]
        for (statement in listOf(
            "UPDATE items SET category = 'first', category = 'second' WHERE __key = ?",
            "INSERT INTO items (id) ignored VALUES (2)",
            "INSERT INTO items (id) VALUES (2),",
        )) {
            assertFailsWith<SqlException>(statement) { sql.execute(statement, if ('?' in statement) listOf(key) else emptyList()) }
        }
        assertEquals(SqlValue.Text("original"), sql.query("SELECT category FROM items").rows.single()[0])
    } }

    @Test fun nullableMutationsPreserveStoredAbsenceAndGuarding() = sqlTest { withSql { sql ->
        val inserted = sql.execute("INSERT INTO items (id, category, note) VALUES (1, NULL, NULL); -- done")
        assertEquals(1, inserted.affectedRows, inserted.response.toString())
        val key = sql.query("SELECT __key FROM items").rows.single()[0]
        assertEquals(listOf(SqlValue.Null, SqlValue.Null), sql.query("SELECT category, note FROM items").rows.single().values)
        sql.execute("UPDATE items SET note = 'stored' WHERE (__key = ?)", listOf(key))
        val version = sql.query("SELECT __version FROM items").rows.single()[0]
        assertEquals(1, sql.execute("UPDATE items SET note /* clear */ = NULL WHERE (? = __key) AND (__version = ?)", listOf(key, version)).affectedRows)
        assertEquals(0, sql.execute("UPDATE items SET note = 'stale' WHERE __key = ? AND __version = ?", listOf(key, version)).affectedRows)
        assertEquals(SqlValue.Null, sql.query("SELECT note FROM items").rows.single()[0])
    } }

    @Test fun writeConversionsRejectWrongTypesAndPrecisionLoss() = sqlTest { withSql { sql ->
        for (statement in listOf(
            "INSERT INTO items (id, category) VALUES (1, TRUE)",
            "INSERT INTO items (id, active) VALUES (1, 'not a boolean')",
            "INSERT INTO items (id, amount) VALUES (1, 1.234)",
        )) assertFailsWith<SqlException>(statement) { sql.execute(statement) }
        assertTrue(sql.query("SELECT id FROM items").rows.isEmpty())
        assertEquals(1, sql.execute("INSERT INTO items (id, amount) VALUES (1, CAST(1.234 AS DECIMAL(4,2)))").affectedRows)
    } }

    @Test fun allMutationRequestsShareOneBudget() = sqlTest { withSql(SqlOptions(allowTableScan = true, maxRequests = 1)) { sql ->
        sql.execute("INSERT INTO items (id) VALUES (1)")
        val key = sql.query("SELECT __key FROM items").rows.single()[0]
        assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { sql.execute("DELETE HARD FROM items WHERE __key = ?", listOf(key)) }.code)
        assertEquals(1, sql.query("SELECT id FROM items").rows.size)
    } }

    @Test fun inferredParametersFailBeforeCollection() = sqlTest { withSql { sql ->
        for (statement in listOf("SELECT COALESCE(?, 1) FROM items", "VALUES (?), (1)", "SELECT CASE WHEN TRUE THEN ? ELSE 1 END FROM items", "SELECT id FROM items WHERE id IN (VALUES (?))", "SELECT id FROM items WHERE (SELECT ?) = id")) {
            assertEquals(SqlErrorCode.PARAMETER, assertFailsWith<SqlException>(statement) { sql.prepare(statement).execute(listOf(SqlValue.Text("wrong"))) }.code)
        }
        for (statement in listOf("SELECT COALESCE(?, 1)", "SELECT CASE WHEN TRUE THEN ? ELSE 1 END")) {
            assertEquals(SqlValue.Exact(Decimal.parse("2")), sql.prepare(statement).execute(listOf(SqlValue.UInt64(2u))).rows.toList().single()[0], statement)
        }
        sql.execute("INSERT INTO items (id) VALUES (1)")
        val key = sql.query("SELECT __key FROM items").rows.single()[0] as SqlValue.Key
        assertEquals(1, sql.query("SELECT id FROM items WHERE __key IN (VALUES (?))", listOf(SqlValue.Text(key.value.toString()))).rows.size)
        assertEquals(1, sql.query("SELECT id FROM items WHERE __key = COALESCE(?, NULL)", listOf(SqlValue.Text(key.value.toString()))).rows.size)
        assertEquals(1, sql.query("SELECT __key FROM items UNION SELECT ?", listOf(SqlValue.Text(key.value.toString()))).rows.size)
        assertEquals(SqlErrorCode.PARAMETER, assertFailsWith<SqlException> {
            sql.prepare("SELECT __key FROM items UNION SELECT ?").execute(listOf(SqlValue.Key("wrong", key.value)))
        }.code)
    } }

    @Test fun nullDoesNotEraseUnconstrainedParameterTypes() = sqlTest { withSql { sql ->
        assertEquals(SqlValue.Text("value"), sql.query("SELECT COALESCE(?, NULL)", listOf(SqlValue.Text("value"))).rows.single()[0])
        assertEquals(listOf(SqlValue.Int64(1), SqlValue.Null), sql.query("VALUES (?), (NULL)", listOf(SqlValue.Int64(1))).rows.map { it[0] })
        assertEquals(SqlValue.Bool(true), sql.query("SELECT CASE WHEN TRUE THEN ? ELSE NULL END", listOf(SqlValue.Bool(true))).rows.single()[0])
    } }

    @Test fun paginationSupportsStandardSelectAndFetchForms() = sqlTest { withSql { sql ->
        assertEquals(listOf(SqlValue.Int64(2)), sql.query("SELECT ALL column1 FROM (VALUES (1), (2), (3)) t ORDER BY 1 OFFSET 1 ROW FETCH NEXT ROW ONLY").rows.map { it[0] })
        assertEquals(1, sql.query("VALUES (1), (2) FETCH FIRST ROW ONLY").rows.size)
    } }

    @Test fun groupingIdentityPreservesBracketsInsideLiterals() = sqlTest { withSql { sql ->
        assertFailsWith<SqlException> {
            sql.prepare("SELECT CASE WHEN category = 'a[b' THEN id ELSE 0 END FROM items GROUP BY CASE WHEN category = 'a[c' THEN id ELSE 0 END")
        }
    } }

    @Test fun commentsAndPunctuationInIdentifiersUseTheSharedLexer() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store, SqlCatalog(listOf(SqlTable("items(--)", SqlTestModel, "a/*b"))), SqlOptions(allowTableScan = true))
            assertEquals(1, sql.execute("INSERT INTO \"a/*b\" /* schema */ . \"items(--)\" (id /* id */, category) VALUES (1, 'a,b)WHERE''SET'); -- tail").affectedRows)
            val key = sql.query("SELECT __key FROM \"a/*b\".\"items(--)\"").rows.single()[0]
            assertEquals(1, sql.execute("UPDATE \"a/*b\".\"items(--)\" SET category = 'ok' WHERE /* key */ __key /* equals */ = ?; -- tail", listOf(key)).affectedRows)
        } finally { store.close() }
    }

    @Test fun deadlinesFailAsLimitsAndKeepTheCallerStoreOpen() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val slow = object : IsDataStore by store, TransactionalDataStore by store {
                override suspend fun executeTransaction(request: TransactionRequest): TransactionResponse {
                    if (request.requests.isNotEmpty()) delay(1000)
                    return store.executeTransaction(request)
                }
                override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(request: RQ): RP {
                    delay(1000)
                    return store.execute(request)
                }
            }
            val sql = MarykSql.create(slow, options = SqlOptions(allowTableScan = true, maxExecutionMillis = 25))
            val execution = sql.prepare("SELECT id FROM SqlTestModel").execute()
            assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { execution.rows.toList() }.code)
            assertEquals(SqlExecutionStatus.FAILED, execution.awaitCompletion().status)
            assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { sql.execute("INSERT INTO SqlTestModel (id) VALUES (1)") }.code)
            assertEquals(1, MarykSql.create(store).execute("INSERT INTO SqlTestModel (id) VALUES (1)").affectedRows)
        } finally { store.close() }
    }
}
