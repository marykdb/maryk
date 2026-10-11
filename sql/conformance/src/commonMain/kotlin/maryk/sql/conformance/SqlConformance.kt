package maryk.sql.conformance

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.models.key
import maryk.core.properties.definitions.decimal
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Decimal
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.SnapshotVersionProvider
import maryk.datastore.shared.TransactionException
import maryk.datastore.shared.TransactionalDataStore
import maryk.datastore.shared.executeTransaction
import maryk.datastore.shared.supportsSnapshotVersion
import maryk.datastore.shared.transactionCapabilities
import maryk.sql.MarykSql
import maryk.sql.SqlCatalog
import maryk.sql.SqlConsistency
import maryk.sql.SqlException
import maryk.sql.SqlExecutionStatus
import maryk.sql.SqlOptions
import maryk.sql.SqlReadOptions
import maryk.sql.SqlSessionResult
import maryk.sql.SqlTable
import maryk.sql.SqlValue
import maryk.sql.SqlWriteStatement
import maryk.sql.beginTransaction
import maryk.sql.session
import maryk.sql.supportsAtomicWrites

/** One fixture and contract shared by every storage adapter. Not a published module. */
object SqlConformanceItem : RootDataModel<SqlConformanceItem>(
    keyDefinition = { SqlConformanceItem.id.ref() },
    indexes = { listOf(SqlConformanceItem.amount.ref(), SqlConformanceItem.category.ref()) },
) {
    val id by number(1u, UInt64, final = true)
    val category by string(2u, required = false)
    val amount by decimal(3u, scale = 2u, required = false)
    val note by string(4u, required = false, default = "not stored")
}

object SqlConformanceOptional : RootDataModel<SqlConformanceOptional>() {
    val value by string(1u, required = false)
}

object SqlConformanceFloating : RootDataModel<SqlConformanceFloating>(
    keyDefinition = { SqlConformanceFloating.id.ref() },
    indexes = { listOf(SqlConformanceFloating.value.ref()) },
) {
    val id by number(1u, UInt64, final = true)
    val value by number(2u, Float64)
}

val sqlConformanceModels = mapOf(1u to SqlConformanceItem, 2u to SqlConformanceOptional, 3u to SqlConformanceFloating)

/** Requires a fresh store with [sqlConformanceModels] and keepAllVersions enabled. */
suspend fun assertSqlConformance(store: IsDataStore) {
    val added = (1..7).map { index ->
        assertIs<AddSuccess<SqlConformanceItem>>(store.execute(SqlConformanceItem.add(SqlConformanceItem.create(setDefaults = false) {
            id with index.toULong()
            category with if (index % 2 == 0) "even" else "odd"
            amount with Decimal.parse("$index.00")
        })).statuses.single())
    }

    val staleGuard = assertIs<ValidationFail<SqlConformanceItem>>(
        store.execute(SqlConformanceItem.delete(added.first().key, hardDelete = true, lastVersion = added.first().version + 1u)).statuses.single(),
    )
    assertTrue(staleGuard.exceptions.isNotEmpty())
    assertEquals(1, store.execute(SqlConformanceItem.get(added.first().key)).values.size, "A stale guarded hard delete must not remove the record")
    val missingKey = SqlConformanceItem.key(ByteArray(SqlConformanceItem.Meta.keyByteSize) { -1 })
    assertIs<DoesNotExist<SqlConformanceItem>>(
        store.execute(SqlConformanceItem.delete(missingKey, hardDelete = true, lastVersion = 1u)).statuses.single(),
    )

    // SQL relies on row identity surviving an all-NULL property projection, including over the wire.
    val projection = SqlConformanceItem.graph { listOf(note) }
    val missing = store.execute(SqlConformanceItem.get(added.first().key, select = projection))
    assertEquals(1, missing.values.size, "An absent selected property must not hide an existing record")
    assertEquals(null, missing.values.single().values.original(SqlConformanceItem.note.index))
    assertTrue(store.execute(SqlConformanceItem.get(added.first().key, select = projection, toVersion = added.first().version - 1u)).values.isEmpty())
    assertEquals(7, store.execute(SqlConformanceItem.scan(select = projection, limit = 10u, allowTableScan = true)).values.size)

    val sql = MarykSql.create(store, SqlCatalog(listOf(SqlTable("items", store.dataModelsById.getValue(1u)))), SqlOptions(allowTableScan = true, pageSize = 2))
    assertEquals(1, sql.execute("INSERT INTO items (id, category, amount) VALUES (8, 'write', 8.00)").affectedRows)
    val writeKey = sql.query("SELECT __key FROM items WHERE id = 8").rows.single()[0]
    assertEquals(1, sql.execute("UPDATE items SET category = 'updated' WHERE __key = ?", listOf(writeKey)).affectedRows)
    assertEquals(SqlValue.Text("updated"), sql.query("SELECT category FROM items WHERE id = 8").rows.single()[0])
    val writeVersion = sql.query("SELECT __version FROM items WHERE id = 8").rows.single()[0]
    assertEquals(1, sql.execute("UPDATE items SET category = 'guarded' WHERE __key = ? AND __version = ?", listOf(writeKey, writeVersion)).affectedRows)
    assertEquals(0, sql.execute("DELETE FROM items WHERE __key = ? AND __version = ?", listOf(writeKey, writeVersion)).affectedRows)
    val currentWriteVersion = sql.query("SELECT __version FROM items WHERE id = 8").rows.single()[0]
    assertEquals(1, sql.execute("DELETE FROM items WHERE __key = ? AND __version = ?", listOf(writeKey, currentWriteVersion)).affectedRows)
    val deletedWriteVersion = sql.query("SELECT __version FROM items WHERE __key = ?", listOf(writeKey), SqlReadOptions(filterSoftDeleted = false)).rows.single()[0]
    assertEquals(1, sql.execute("UNDELETE FROM items WHERE __key = ? AND __version = ?", listOf(writeKey, deletedWriteVersion)).affectedRows)
    val restoredWriteVersion = sql.query("SELECT __version FROM items WHERE id = 8").rows.single()[0]
    assertEquals(1, sql.execute("UPDATE items SET category = 'hard-delete' WHERE __key = ?", listOf(writeKey)).affectedRows)
    assertEquals(0, sql.execute("DELETE HARD FROM items WHERE __key = ? AND __version = ?", listOf(writeKey, restoredWriteVersion)).affectedRows)
    val hardDeleteWriteVersion = sql.query("SELECT __version FROM items WHERE id = 8").rows.single()[0]
    assertEquals(1, sql.execute("DELETE HARD FROM items WHERE __key = ? AND __version = ?", listOf(writeKey, hardDeleteWriteVersion)).affectedRows)
    val ordered = sql.query("SELECT id, amount FROM items WHERE amount >= ? AND id NOT IN (1, 2) ORDER BY id DESC LIMIT 3 OFFSET 1", listOf(SqlValue.Exact(Decimal.parse("2.500"))))
    assertEquals(listOf(6uL, 5uL, 4uL), ordered.rows.map { (it[0] as SqlValue.UInt64).value })
    val grouped = sql.query("SELECT category, COUNT(*), SUM(amount), AVG(amount) FROM items GROUP BY category ORDER BY category")
    assertEquals(listOf(3L, 4L), grouped.rows.map { (it[1] as SqlValue.Int64).value })
    assertEquals(listOf("12.00", "16.00"), grouped.rows.map { (it[2] as SqlValue.Exact).value.toString() })
    assertEquals(listOf("4.000000", "4.000000"), grouped.rows.map { (it[3] as SqlValue.Exact).value.toString() })
    assertEquals(7, sql.query("SELECT note FROM items WHERE note IS NULL").rows.size)
    assertTrue(sql.query("SELECT id FROM items WHERE id NOT IN (1, NULL)").rows.isEmpty())
    assertEquals(SqlValue.Int64(0), sql.query("SELECT COUNT(*) FROM items WHERE id > 100").rows.single()[0])
    assertEquals(SqlValue.Null, sql.query("SELECT SUM(amount) FROM items WHERE id > 100").rows.single()[0])
    assertEquals(2, sql.query("SELECT DISTINCT category FROM items").rows.size)
    val joined = sql.query("WITH selected AS (SELECT id FROM items WHERE id <= 2) SELECT a.id, b.id FROM selected a LEFT JOIN items b ON a.id = b.id AND b.id = 1 ORDER BY a.id")
    assertEquals(listOf(SqlValue.UInt64(1uL), SqlValue.UInt64(2uL)), joined.rows.map { it[0] })
    assertEquals(listOf(SqlValue.UInt64(1uL), SqlValue.Null), joined.rows.map { it[1] })
    assertEquals(
        listOf(SqlValue.UInt64(6uL), SqlValue.UInt64(7uL)),
        sql.query("SELECT id FROM items WHERE id IN (SELECT id FROM items WHERE amount >= 6) ORDER BY id").rows.map { it[0] },
    )
    val keyOnly = MarykSql.create(store, sql.catalog).prepare("SELECT __key FROM items WHERE __key = ?")
        .execute(listOf(SqlValue.Text(added.first().key.toString())))
    assertEquals(added.first().key, (keyOnly.rows.toList().single()[0] as SqlValue.Key).value)
    assertEquals(1, keyOnly.awaitCompletion().storeRequests)
    val limited = sql.prepare("SELECT id FROM items LIMIT 1").execute()
    assertEquals(1, limited.rows.toList().size)
    assertEquals(SqlExecutionStatus.SUCCEEDED, limited.awaitCompletion().status)
    val zero = sql.prepare("SELECT id FROM items LIMIT 0").execute()
    assertTrue(zero.rows.toList().isEmpty())
    assertEquals(0, zero.awaitCompletion().storeRequests)

    if (store is SnapshotVersionProvider) {
        val snapshot = sql.prepare("SELECT COUNT(*) FROM items").execute(options = SqlReadOptions(SqlConsistency.Snapshot))
        assertEquals(SqlValue.Int64(7), snapshot.rows.toList().single()[0])
        assertTrue(snapshot.awaitCompletion().snapshotVersion != null)
        val atFirst = sql.query("SELECT id FROM items ORDER BY id", readOptions = SqlReadOptions(SqlConsistency.AtVersion(added.first().version)))
        assertEquals(listOf(1uL), atFirst.rows.map { (it[0] as SqlValue.UInt64).value })
    }

    val emptyProjection = SqlConformanceItem.graph { emptyList() }
    val deleted = assertIs<DeleteSuccess<SqlConformanceItem>>(
        store.execute(SqlConformanceItem.delete(added.first().key)).statuses.single(),
    )
    val included = store.execute(SqlConformanceItem.get(added.first().key, select = emptyProjection, filterSoftDeleted = false))
    assertTrue(included.values.single().isDeleted, "An empty projection must preserve soft-delete metadata")
    assertTrue(included.values.single().values.size == 0)
    assertTrue(store.execute(SqlConformanceItem.get(added.first().key, select = emptyProjection)).values.isEmpty())
    val beforeDelete = store.execute(SqlConformanceItem.get(added.first().key, select = emptyProjection, filterSoftDeleted = false, toVersion = added.last().version))
    assertEquals(false, beforeDelete.values.single().isDeleted)
    val afterDelete = store.execute(SqlConformanceItem.get(added.first().key, select = emptyProjection, filterSoftDeleted = false, toVersion = deleted.version))
    assertTrue(afterDelete.values.single().isDeleted)
    val firstPage = store.execute(SqlConformanceItem.scan(select = emptyProjection, filterSoftDeleted = false, limit = 1u, allowTableScan = true))
    assertTrue(firstPage.values.single().isDeleted)
    val nextPage = store.execute(SqlConformanceItem.scan(select = emptyProjection, filterSoftDeleted = false, limit = 1u, allowTableScan = true, cursor = firstPage.nextCursor))
    assertEquals(added[1].key, nextPage.values.single().key)
    val historicalPage = store.execute(SqlConformanceItem.scan(select = emptyProjection, filterSoftDeleted = false, limit = 1u, allowTableScan = true, toVersion = deleted.version))
    assertTrue(historicalPage.values.single().isDeleted)
    assertEquals(SqlValue.Int64(6), sql.query("SELECT COUNT(*) FROM items").rows.single()[0])
    assertEquals(SqlValue.Int64(7), sql.query("SELECT COUNT(*) FROM items", readOptions = SqlReadOptions(filterSoftDeleted = false)).rows.single()[0])
    store.execute(SqlConformanceItem.delete(added.first().key, hardDelete = true))
    assertTrue(store.execute(SqlConformanceItem.get(added.first().key, select = emptyProjection, filterSoftDeleted = false)).values.isEmpty())

    assertEquals(1, sql.execute("INSERT INTO items (id, category, note) VALUES (9, '😀', NULL)").affectedRows)
    val nullableKey = sql.query("SELECT __key FROM items WHERE id = 9").rows.single()[0]
    assertEquals(SqlValue.Null, sql.query("SELECT note FROM items WHERE id = 9").rows.single()[0])
    assertEquals(1, sql.execute("UPDATE items SET note = 'present' WHERE __key = ?", listOf(nullableKey)).affectedRows)
    assertEquals(1, sql.execute("UPDATE items SET note = NULL WHERE __key = ?", listOf(nullableKey)).affectedRows)
    assertEquals(SqlValue.Null, sql.query("SELECT note FROM items WHERE id = 9").rows.single()[0])
    // Supplementary characters precede U+E000 in SQL's UTF-16 order, but follow it in
    // the UTF-8 ordering used by persistent indexes. Residual filtering must retain them.
    assertEquals(listOf(SqlValue.UInt64(9u)), sql.query("SELECT id FROM items WHERE category > 'z' AND category < ''").rows.map { it[0] })
    assertEquals(1, sql.execute("DELETE FROM items WHERE __key = ?", listOf(nullableKey)).affectedRows)
    assertEquals(1, sql.execute("DELETE HARD FROM items WHERE __key = ?", listOf(nullableKey)).affectedRows)
    assertTrue(sql.query("SELECT id FROM items WHERE __key = ?", listOf(nullableKey), SqlReadOptions(filterSoftDeleted = false)).rows.isEmpty())

    val optionalSql = MarykSql.create(store, SqlCatalog(listOf(SqlTable("optional_rows", store.dataModelsById.getValue(2u)))), SqlOptions(allowTableScan = true))
    assertEquals(1, optionalSql.execute("INSERT INTO optional_rows (value) VALUES (NULL)").affectedRows)
    val emptyRow = optionalSql.query("SELECT * FROM optional_rows").rows.single()
    assertEquals(SqlValue.Null, emptyRow[2])
    val optionalKey = emptyRow[0]
    assertEquals(1, optionalSql.execute("UPDATE optional_rows SET value = 'present' WHERE __key = ?", listOf(optionalKey)).affectedRows)
    assertEquals(1, optionalSql.execute("UPDATE optional_rows SET value = NULL WHERE __key = ?", listOf(optionalKey)).affectedRows)
    assertEquals(SqlValue.Null, optionalSql.query("SELECT * FROM optional_rows").rows.single()[2])
    assertEquals(1, optionalSql.execute("DELETE FROM optional_rows WHERE __key = ?", listOf(optionalKey)).affectedRows)
    assertEquals(1, optionalSql.execute("UNDELETE FROM optional_rows WHERE __key = ?", listOf(optionalKey)).affectedRows)
    assertEquals(1, optionalSql.execute("DELETE HARD FROM optional_rows WHERE __key = ?", listOf(optionalKey)).affectedRows)

    val floatingSql = MarykSql.create(store, SqlCatalog(listOf(SqlTable("floating_rows", store.dataModelsById.getValue(3u)))), SqlOptions(allowTableScan = true))
    assertEquals(2, floatingSql.execute("INSERT INTO floating_rows (id, value) VALUES (1, -0.0e0), (2, 0.0e0)").affectedRows)
    for (predicate in listOf("value = 0.0e0", "value >= 0.0e0", "value <= -0.0e0")) {
        assertEquals(listOf(SqlValue.UInt64(1u), SqlValue.UInt64(2u)), floatingSql.query("SELECT id FROM floating_rows WHERE $predicate ORDER BY id").rows.map { it[0] })
    }

    if (sql.supportsAtomicWrites) {
        val atomicSql = MarykSql.create(store, SqlCatalog(listOf(
            SqlTable("items", store.dataModelsById.getValue(1u)), SqlTable("optional_rows", store.dataModelsById.getValue(2u)),
        )), SqlOptions(allowTableScan = true, pageSize = 1))
        val prepared = atomicSql.prepareAtomic("sql-conformance-insert", listOf(
            SqlWriteStatement("INSERT INTO items (id, category) VALUES (101, 'atomic'), (102, 'atomic') RETURNING id, __version"),
            SqlWriteStatement("INSERT INTO optional_rows (value) VALUES ('atomic') RETURNING __key, value"),
        ))
        val inserted = prepared.execute()
        assertEquals(AtomicStatus.Committed, inserted.response.status)
        assertEquals(3, inserted.affectedRows)
        assertEquals(inserted.response, prepared.execute().response)
        assertEquals(inserted.returning[0].rows.first()[1], inserted.returning[0].rows.last()[1])
        val rejected = atomicSql.executeAtomic("sql-conformance-rollback", "INSERT INTO items (id) VALUES (103), (101) RETURNING id")
        assertEquals(AtomicStatus.Rejected, rejected.response.status)
        assertEquals(0, rejected.affectedRows)
        assertTrue(rejected.returning.single().rows.isEmpty())
        assertTrue(atomicSql.query("SELECT id FROM items WHERE id = 103").rows.isEmpty())
        val guarded = atomicSql.prepareAtomic("sql-conformance-guard", "UPDATE items SET category = 'guarded' WHERE category = 'atomic' RETURNING id")
        val updated = atomicSql.executeAtomic("sql-conformance-update", "UPDATE items SET category = 'updated' WHERE id IN (101, 102) RETURNING category")
        assertEquals(2, updated.affectedRows)
        assertEquals(listOf(SqlValue.Text("updated"), SqlValue.Text("updated")), updated.returning.single().rows.map { it[0] })
        assertEquals(AtomicStatus.Rejected, guarded.execute().response.status)
        val copied = atomicSql.executeAtomic("sql-conformance-copy", "INSERT INTO items (id, category) SELECT id + 100, category FROM items WHERE id IN (101, 102) RETURNING id")
        assertEquals(2, copied.affectedRows)
        assertEquals(2, atomicSql.executeAtomic("sql-conformance-delete", "DELETE FROM items WHERE id IN (201, 202) RETURNING id").affectedRows)
        assertEquals(2, atomicSql.executeAtomic("sql-conformance-restore", "UNDELETE FROM items WHERE id IN (201, 202) RETURNING id").affectedRows)
    }
    assertSqlTransactionConformance(store, sql)

}

/** Ordered SQL reads/writes, savepoints and commit recovery run through every backend. */
private suspend fun assertSqlTransactionConformance(store: IsDataStore, sql: MarykSql) {
    val tx = sql.beginTransaction()
    val inserted = tx.execute("INSERT INTO items (id, category, amount) VALUES (301, 'first', 1.00), (302, 'first', 2.00) RETURNING id, __version")
    assertEquals(AtomicStatus.Staged, inserted.response.status)
    assertTrue(sql.query("SELECT id FROM items WHERE id >= 300").rows.isEmpty())
    assertEquals(2, tx.query("SELECT id FROM items WHERE category = 'first'").rows.size)
    val point = tx.savepoint()
    assertEquals(2, tx.execute("UPDATE items SET category = 'discard' WHERE category = 'first'").affectedRows)
    tx.rollbackTo(point)
    assertEquals(2, tx.query("SELECT id FROM items WHERE category = 'first'").rows.size)
    assertEquals(2, tx.execute("UPDATE items SET category = 'second', amount = amount + 10 WHERE category = 'first' RETURNING category, amount").affectedRows)
    assertEquals(2, tx.execute("INSERT INTO items (id, category, amount) SELECT id + 10, category, amount FROM items WHERE category = 'second' RETURNING id").affectedRows)
    assertEquals(2, tx.execute("DELETE FROM items WHERE id IN (301, 302) RETURNING id").affectedRows)
    assertEquals(2, tx.execute("UNDELETE FROM items WHERE id IN (301, 302) RETURNING id").affectedRows)
    assertEquals(2, tx.execute("DELETE HARD FROM items WHERE id IN (311, 312) RETURNING id").affectedRows)
    assertEquals(TransactionStatus.Committed, tx.commit().status)
    assertEquals(listOf(SqlValue.UInt64(301u), SqlValue.UInt64(302u)), sql.query("SELECT id FROM items WHERE id >= 300 ORDER BY id").rows.map { it[0] })
    val committedVersion = (sql.query("SELECT __version FROM items WHERE id = 301").rows.single()[0] as SqlValue.UInt64).value
    val historical = sql.beginTransaction()
    historical.execute("UPDATE items SET category = 'staged-history' WHERE id = 301")
    if (store.supportsSnapshotVersion) {
        assertEquals(SqlValue.Text("second"), historical.query("SELECT category FROM items WHERE id = 301", readOptions = SqlReadOptions(SqlConsistency.AtVersion(committedVersion))).rows.single()[0])
    } else {
        assertFailsWith<SqlException> { historical.query("SELECT category FROM items WHERE id = 301", readOptions = SqlReadOptions(SqlConsistency.AtVersion(committedVersion))) }
    }
    assertEquals(SqlValue.Text("staged-history"), historical.query("SELECT category FROM items WHERE id = 301").rows.single()[0])
    historical.rollback()

    val session = sql.session()
    session.execute("/* transactional */ BEGIN")
    session.execute("SAVEPOINT recovery")
    assertFailsWith<TransactionException> { session.execute("INSERT INTO items (id) VALUES (303), (301)") }
    session.execute("ROLLBACK TO recovery")
    session.execute("INSERT INTO items (id, category) VALUES (303, 'recovered')")
    session.execute("RELEASE recovery")
    val result = session.execute("COMMIT") as SqlSessionResult.Control
    assertEquals(TransactionStatus.Committed, result.commit?.status)
    session.execute("BEGIN READ ONLY")
    assertFailsWith<SqlException> { session.execute("UPDATE items SET category = 'denied' WHERE id = 99999") }
    assertFailsWith<SqlException> { session.execute("DELETE HARD FROM items WHERE id = 99999") }
    assertEquals(1, (session.execute("SELECT id FROM items WHERE id = 303") as SqlSessionResult.Query).result.rows.size)
    session.execute("ROLLBACK")
    session.execute("BEGIN")
    session.execute("DELETE HARD FROM items WHERE id = 303")
    session.close()
    assertEquals(1, sql.query("SELECT id FROM items WHERE id = 303").rows.size)

    val conflict = sql.beginTransaction()
    conflict.query("SELECT id FROM items WHERE id = 304")
    sql.execute("INSERT INTO items (id, category) VALUES (304, 'concurrent')")
    val rejected = conflict.commit()
    assertEquals(TransactionStatus.Aborted, rejected.status)
    assertEquals(AtomicFailureCode.Conflict, rejected.failure?.code)

    var lose = true
    val transport = object : IsDataStore by store, TransactionalDataStore {
        override val transactionCapabilities get() = store.transactionCapabilities
        override suspend fun executeTransaction(request: TransactionRequest) = store.executeTransaction(request).also {
            if (request.commit && lose) { lose = false; error("Lost SQL commit response") }
        }
    }
    val unreliableSql = MarykSql.create(transport, sql.catalog, sql.options)
    val uncertain = unreliableSql.execute("INSERT INTO items (id, category) VALUES (305, 'recovered-commit') RETURNING id")
    assertEquals(AtomicStatus.OutcomeUnknown, uncertain.response.status)
    assertEquals(0, uncertain.affectedRows)
    assertTrue(uncertain.returning.all { it.rows.isEmpty() })
    assertEquals(TransactionStatus.Committed, requireNotNull(uncertain.transaction).resolveCommit().status)
    assertEquals(1, sql.query("SELECT id FROM items WHERE id = 305").rows.size)
    val durable = sql.execute("UPDATE items SET category = 'durable' WHERE id >= 300 RETURNING id, __version")
    assertEquals(sql.query("SELECT id, __version FROM items WHERE id >= 300 ORDER BY id").rows.map { it.values }, durable.returning.single().rows.map { it.values })
}
