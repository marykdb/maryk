package maryk.sql

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.query.responses.AtomicStatus
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.IsDataStore
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private object AtomicSqlGeneratedKeyModel : RootDataModel<AtomicSqlGeneratedKeyModel>() {
    val value by string(1u)
}

class SqlAtomicWriteTest {
    private suspend fun withSql(options: SqlOptions = SqlOptions(allowTableScan = true), block: suspend (MarykSql, IsDataStore) -> Unit) {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel, 2u to AtomicSqlGeneratedKeyModel))
        try {
            block(MarykSql.create(store, SqlCatalog(listOf(SqlTable("items", SqlTestModel), SqlTable("generated", AtomicSqlGeneratedKeyModel))), options), store)
        } finally { store.close() }
    }

    @Test
    fun capabilityReportAndStructuredPlanningLimit() = sqlTest {
        withSql { sql, _ ->
            assertEquals(1000u, sql.atomicWriteCapabilities?.store?.maxMutations)
            assertEquals(1000u, sql.atomicWriteCapabilities?.maxStatements)
            val exception = assertFailsWith<SqlException> {
                sql.prepareAtomic("too-many-statements", List(1001) { SqlWriteStatement("INSERT INTO items (id) VALUES (1)") })
            }
            val limit = assertIs<SqlLimitException>(exception)
            assertEquals("statements", limit.limit)
            assertEquals(1000uL, limit.maximum)
            assertEquals(1001uL, limit.actual)
        }
    }

    @Test
    fun thousandSqlRowsCommitAndReplay() = sqlTest {
        withSql { sql, _ ->
            val rows = (1..1000).joinToString { "($it)" }
            val prepared = sql.prepareAtomic("thousand", "INSERT INTO items (id) VALUES $rows RETURNING id")
            val committed = prepared.execute()
            assertEquals(AtomicStatus.Committed, committed.response.status)
            assertEquals(1000, committed.affectedRows)
            assertEquals(1000, committed.returning.single().rows.size)
            assertEquals(committed.response, prepared.execute().response)
        }
    }

    @Test
    fun atomicInsertReturningAndRetryPreserveGeneratedKeys() = sqlTest {
        withSql { sql, _ ->
            val prepared = sql.prepareAtomic("insert-retry", "INSERT INTO generated (value) VALUES ('a'), ('b') RETURNING __key, __version, value AS name")
            val first = prepared.execute()
            assertEquals(AtomicStatus.Committed, first.response.status)
            assertEquals(2, first.affectedRows)
            assertEquals(listOf("__key", "__version", "name"), first.returning.single().columns.map { it.name })
            val rows = first.returning.single().rows
            assertNotEquals(rows[0][0], rows[1][0])
            assertEquals(rows[0][1], rows[1][1])
            val again = prepared.execute()
            assertEquals(first.response, again.response)
            assertEquals(rows.map { it.values }, again.returning.single().rows.map { it.values })
            assertEquals(SqlValue.Int64(2), sql.query("SELECT COUNT(*) FROM generated").rows.single()[0])
        }
    }

    @Test
    fun lateDuplicateRollsBackEveryInsert() = sqlTest {
        withSql { sql, _ ->
            sql.execute("INSERT INTO items (id) VALUES (2)")
            val result = sql.executeAtomic("duplicate", "INSERT INTO items (id) VALUES (1), (2) RETURNING id")
            assertEquals(AtomicStatus.Rejected, result.response.status)
            assertEquals(0, result.affectedRows)
            assertTrue(result.returning.single().rows.isEmpty())
            assertEquals(listOf(SqlValue.UInt64(2u)), sql.query("SELECT id FROM items").rows.map { it[0] })
        }
    }

    @Test
    fun statementsAcrossModelsCommitTogether() = sqlTest {
        withSql { sql, _ ->
            val result = sql.executeAtomic("batch", listOf(
                SqlWriteStatement("INSERT INTO items (id, category) VALUES (?, ?)", listOf(SqlValue.UInt64(1u), SqlValue.Text("batch"))),
                SqlWriteStatement("INSERT INTO generated (value) VALUES (?) RETURNING value", listOf(SqlValue.Text("other model"))),
            ))
            assertEquals(AtomicStatus.Committed, result.response.status)
            assertEquals(2, result.affectedRows)
            assertTrue(result.returning[0].columns.isEmpty())
            assertEquals(SqlValue.Text("other model"), result.returning[1].rows.single()[0])
        }
    }

    @Test
    fun arbitraryPredicateUpdatesReturnPostImages() = sqlTest {
        withSql { sql, _ ->
            sql.execute("INSERT INTO items (id, category, amount) VALUES (1, 'a', 1), (2, 'a', 2), (3, 'b', 3)")
            val result = sql.executeAtomic("update-many", "UPDATE items SET category = 'changed', amount = amount + 10, note = NULL WHERE category = ? RETURNING id, category, amount, note, __version", listOf(SqlValue.Text("a")))
            assertEquals(AtomicStatus.Committed, result.response.status)
            assertEquals(2, result.affectedRows)
            val rows = result.returning.single().rows
            assertEquals(listOf("11.00", "12.00"), rows.map { it[2].display() })
            rows.forEach { assertEquals(SqlValue.Text("changed"), it[1]); assertEquals(SqlValue.Null, it[3]) }
            assertEquals(rows.map { it.values }, sql.query("SELECT id, category, amount, note, __version FROM items WHERE category = 'changed' ORDER BY id").rows.map { it.values })
        }
    }

    @Test
    fun staleSelectedRecordRejectsWholeChangeSet() = sqlTest {
        withSql { sql, _ ->
            sql.execute("INSERT INTO items (id, category) VALUES (1, 'old'), (2, 'old')")
            val prepared = sql.prepareAtomic("guarded", listOf(
                SqlWriteStatement("UPDATE items SET category = 'new' WHERE category = 'old' RETURNING id"),
                SqlWriteStatement("INSERT INTO generated (value) VALUES ('must roll back')"),
            ))
            sql.executeAtomic("competitor", "UPDATE items SET category = 'competitor' WHERE id = 2")
            val result = prepared.execute()
            assertEquals(AtomicStatus.Rejected, result.response.status)
            assertEquals(0, result.affectedRows)
            assertTrue(result.returning.all { it.rows.isEmpty() })
            assertEquals(listOf(SqlValue.Text("old"), SqlValue.Text("competitor")), sql.query("SELECT category FROM items ORDER BY id").rows.map { it[0] })
            assertTrue(sql.query("SELECT * FROM generated").rows.isEmpty())
        }
    }

    @Test
    fun deleteReturnsBeforeImagesAndUndeleteRestoresManyRows() = sqlTest {
        withSql { sql, _ ->
            sql.execute("INSERT INTO items (id, category) VALUES (1, 'a'), (2, 'a')")
            val deleted = sql.executeAtomic("delete-many", "DELETE FROM items WHERE category = 'a' RETURNING id, category")
            assertEquals(2, deleted.affectedRows)
            assertEquals(listOf(SqlValue.UInt64(1u), SqlValue.UInt64(2u)), deleted.returning.single().rows.map { it[0] })
            assertTrue(sql.query("SELECT * FROM items").rows.isEmpty())
            val restored = sql.executeAtomic("restore-many", "UNDELETE FROM items WHERE id IN (1, 2) RETURNING *")
            assertEquals(2, restored.affectedRows)
            assertEquals(2, sql.query("SELECT * FROM items").rows.size)
            assertEquals(7, restored.returning.single().columns.size)
        }
    }

    @Test
    fun insertSelectSupportsExpressionsParametersAndReturning() = sqlTest {
        withSql { sql, _ ->
            sql.execute("INSERT INTO items (id, category) VALUES (1, 'a'), (2, 'b')")
            val result = sql.executeAtomic("insert-select", "INSERT INTO items (id, category) SELECT id + ?, category FROM items ORDER BY id RETURNING id, category", listOf(SqlValue.UInt64(10u)))
            assertEquals(2, result.affectedRows)
            assertEquals(listOf(SqlValue.UInt64(11u), SqlValue.UInt64(12u)), result.returning.single().rows.map { it[0] })
            assertEquals(4, sql.query("SELECT * FROM items").rows.size)
        }
    }

    @Test
    fun emptySelectionHasNoCommitOrReturningRows() = sqlTest {
        withSql { sql, _ ->
            val prepared = sql.prepareAtomic("empty", "UPDATE items SET category = 'none' WHERE id = 42 RETURNING id")
            assertEquals(null, prepared.request)
            val result = prepared.execute()
            assertEquals(0, result.affectedRows)
            assertEquals(AtomicStatus.Committed, result.response.status)
            assertTrue(result.returning.single().rows.isEmpty())
            assertEquals(listOf("id"), result.returning.single().columns.map { it.name })
        }
    }

    @Test
    fun atomicLimitsNeverSplitWrites() = sqlTest {
        withSql { sql, _ ->
            val rows = (1..1001).joinToString { "($it)" }
            assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { sql.prepareAtomic("too-many", "INSERT INTO items (id) VALUES $rows") }.code)
            assertTrue(sql.query("SELECT * FROM items").rows.isEmpty())
            sql.execute("INSERT INTO items (id) VALUES " + (1..1000).joinToString { "($it)" })
            sql.execute("INSERT INTO items (id) VALUES (1001)")
            assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { sql.prepareAtomic("update-too-many", "UPDATE items SET category = 'no' WHERE TRUE") }.code)
            assertEquals(SqlValue.Int64(1001), sql.query("SELECT COUNT(*) FROM items WHERE category IS NULL").rows.single()[0])
        }
    }

    @Test
    fun duplicateTargetsAndHardDeletesAreRejectedBeforeWrites() = sqlTest {
        withSql { sql, _ ->
            sql.execute("INSERT INTO items (id) VALUES (1)")
            assertEquals(SqlErrorCode.UNSUPPORTED, assertFailsWith<SqlException> { sql.prepareAtomic("duplicate-target", listOf(
                SqlWriteStatement("UPDATE items SET category = 'a' WHERE id = 1"),
                SqlWriteStatement("DELETE FROM items WHERE id = 1"),
            )) }.code)
            assertEquals(SqlErrorCode.UNSUPPORTED, assertFailsWith<SqlException> { sql.prepareAtomic("hard", "DELETE HARD FROM items WHERE id = 1") }.code)
            assertEquals(SqlValue.Null, sql.query("SELECT category FROM items").rows.single()[0])
        }
    }

    @Test
    fun executeSupportsTransactionalPredicatesReturningAndInsertQueries() = sqlTest {
        withSql { sql, _ ->
            assertEquals(0, sql.execute("UPDATE items SET category = 'a' WHERE TRUE").affectedRows)
            assertEquals(1, sql.execute("INSERT INTO items (id) VALUES (1) RETURNING id").affectedRows)
            assertEquals(1, sql.execute("INSERT INTO items (id) SELECT 2").affectedRows)
            assertEquals(2, sql.query("SELECT * FROM items").rows.size)
        }
    }

    @Test
    fun unsupportedStoreFailsBeforePlanningReads() = sqlTest {
        withSql { sql, store ->
            val unsupported = object : IsDataStore by store {}
            val facade = MarykSql.create(unsupported, sql.catalog)
            assertEquals(false, facade.supportsAtomicWrites)
            assertEquals(SqlErrorCode.UNSUPPORTED, assertFailsWith<SqlException> { facade.prepareAtomic("unsupported", "UPDATE items SET category = 'no' WHERE TRUE") }.code)
        }
    }

    @Test
    fun returningExpressionsAndBadParametersFailBeforeCommit() = sqlTest {
        withSql { sql, _ ->
            assertEquals(SqlErrorCode.UNSUPPORTED, assertFailsWith<SqlException> { sql.prepareAtomic("return-expression", "INSERT INTO items (id) VALUES (1) RETURNING id + 1") }.code)
            assertEquals(SqlErrorCode.PARAMETER, assertFailsWith<SqlException> { sql.prepareAtomic("parameter", "INSERT INTO items (id) VALUES (?)") }.code)
            assertTrue(sql.query("SELECT * FROM items").rows.isEmpty())
        }
    }

    @Test
    fun returningRetryKeepsCommitImagesAfterLaterWrites() = sqlTest {
        withSql { sql, _ ->
            val prepared = sql.prepareAtomic("return-image", "INSERT INTO items (id, category) VALUES (1, 'original') RETURNING *")
            val original = prepared.execute()
            sql.executeAtomic("later-write", "UPDATE items SET category = 'later' WHERE id = 1")
            assertEquals(original.returning.single().rows.single().values, prepared.execute().returning.single().rows.single().values)
            assertEquals(SqlValue.Text("later"), sql.query("SELECT category FROM items").rows.single()[0])
        }
    }

    @Test
    fun planningReadsPreBatchStateAndDoesNotFillOmittedDefaults() = sqlTest {
        withSql { sql, _ ->
            val prepared = sql.prepareAtomic("pre-batch", listOf(
                SqlWriteStatement("INSERT INTO items (id) VALUES (1) RETURNING note"),
                SqlWriteStatement("INSERT INTO generated (value) SELECT category FROM items WHERE id = 1 RETURNING value"),
            ))
            assertTrue(sql.query("SELECT * FROM items").rows.isEmpty())
            val result = prepared.execute()
            assertEquals(1, result.affectedRows)
            assertEquals(SqlValue.Null, result.returning[0].rows.single()[0])
            assertTrue(result.returning[1].rows.isEmpty())
        }
    }

    @Test
    fun insertQueriesSupportCtesAndRejectWrongWidths() = sqlTest {
        withSql { sql, _ ->
            val inserted = sql.executeAtomic("cte", "INSERT INTO items (id, category) WITH source AS (VALUES (1, 'one'), (2, 'two')) SELECT * FROM source RETURNING items.id AS item")
            assertEquals(listOf(SqlValue.UInt64(1u), SqlValue.UInt64(2u)), inserted.returning.single().rows.map { it[0] })
            assertEquals(listOf("item"), inserted.returning.single().columns.map { it.name })
            assertEquals(SqlErrorCode.SYNTAX, assertFailsWith<SqlException> { sql.prepareAtomic("width", "INSERT INTO items (id) SELECT 3, 'too many'") }.code)
        }
    }

    @Test
    fun tableScanPermissionAndBatchRowLimitRemainEffective() = sqlTest {
        withSql(SqlOptions(maxResultRows = 1, allowTableScan = true)) { sql, _ ->
            assertEquals(SqlErrorCode.LIMIT, assertFailsWith<SqlException> { sql.prepareAtomic("budget", "INSERT INTO items (id) VALUES (1), (2)") }.code)
            assertTrue(sql.query("SELECT * FROM items").rows.isEmpty())
        }
        withSql(SqlOptions(allowTableScan = false)) { sql, _ ->
            assertFailsWith<SqlException> { sql.prepareAtomic("scan-denied", "UPDATE items SET category = 'no' WHERE TRUE") }
        }
    }
}
