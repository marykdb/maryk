package maryk.sql.conformance

import kotlinx.coroutines.flow.toList
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.decimal
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Decimal
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.SnapshotVersionProvider
import maryk.sql.MarykSql
import maryk.sql.SqlCatalog
import maryk.sql.SqlConsistency
import maryk.sql.SqlExecutionStatus
import maryk.sql.SqlOptions
import maryk.sql.SqlReadOptions
import maryk.sql.SqlTable
import maryk.sql.SqlValue
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** One fixture and contract shared by every storage adapter. Not a published module. */
object SqlConformanceItem : RootDataModel<SqlConformanceItem>(
    keyDefinition = { SqlConformanceItem.id.ref() },
    indexes = { listOf(SqlConformanceItem.amount.ref()) },
) {
    val id by number(1u, UInt64, final = true)
    val category by string(2u, required = false)
    val amount by decimal(3u, scale = 2u, required = false)
    val note by string(4u, required = false, default = "not stored")
}

val sqlConformanceModels = mapOf(1u to SqlConformanceItem)

/** Requires a fresh store with [sqlConformanceModels] and keepAllVersions enabled. */
suspend fun assertSqlConformance(store: IsDataStore) {
    val added = (1..7).map { index ->
        assertIs<AddSuccess<SqlConformanceItem>>(store.execute(SqlConformanceItem.add(SqlConformanceItem.create(setDefaults = false) {
            id with index.toULong()
            category with if (index % 2 == 0) "even" else "odd"
            amount with Decimal.parse("$index.00")
        })).statuses.single())
    }

    // SQL relies on row identity surviving an all-NULL property projection, including over the wire.
    val projection = SqlConformanceItem.graph { listOf(note) }
    val missing = store.execute(SqlConformanceItem.get(added.first().key, select = projection))
    assertEquals(1, missing.values.size, "An absent selected property must not hide an existing record")
    assertEquals(null, missing.values.single().values.original(SqlConformanceItem.note.index))
    assertTrue(store.execute(SqlConformanceItem.get(added.first().key, select = projection, toVersion = added.first().version - 1u)).values.isEmpty())
    assertEquals(7, store.execute(SqlConformanceItem.scan(select = projection, limit = 10u, allowTableScan = true)).values.size)

    val sql = MarykSql.create(store, SqlCatalog(listOf(SqlTable("items", store.dataModelsById.getValue(1u)))), SqlOptions(allowTableScan = true, pageSize = 2))
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
}
