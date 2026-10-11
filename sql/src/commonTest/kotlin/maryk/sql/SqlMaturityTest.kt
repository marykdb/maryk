package maryk.sql

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsUsableInMapValue
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.Decimal
import maryk.core.query.requests.add
import maryk.test.models.TestMarykModel
import maryk.datastore.shared.IsDataStore
import maryk.datastore.memory.InMemoryDataStore
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import maryk.core.query.responses.TransactionStatus
import kotlin.random.Random
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import maryk.core.query.responses.AtomicStatus

class SqlMaturityTest {
    @Test
    fun topNAndSimpleAggregatesUseBoundedStateAcrossPages() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            for (i in 1..200) store.execute(SqlTestModel.add(SqlTestModel.create(setDefaults = false) {
                id with i.toULong()
                category with if (i % 2 == 0) "even" else "odd"
                amount with Decimal.parse("$i.00")
            }))
            val sql = MarykSql.create(store, options = SqlOptions(allowTableScan = true, pageSize = 7, maxBufferedBytes = 4096))
            assertEquals(listOf(197uL, 196uL, 195uL), sql.query("SELECT id FROM SqlTestModel ORDER BY id DESC LIMIT 3 OFFSET 3").rows.map { (it[0] as SqlValue.UInt64).value })
            val aggregate = sql.query("SELECT COUNT(*), SUM(amount), AVG(amount), MIN(amount), MAX(amount) FROM SqlTestModel").rows.single()
            assertEquals(SqlValue.Int64(200), aggregate[0])
            assertEquals("20100.00", aggregate[1].display())
            assertEquals("100.500000", aggregate[2].display())
            assertEquals("20101.00", sql.query("SELECT SUM(amount) + 1 FROM SqlTestModel").rows.single()[0].display())
            assertEquals(listOf(100L, 100L), sql.query("SELECT COUNT(*) FROM SqlTestModel GROUP BY category HAVING COUNT(*) >= 100").rows.map { (it[0] as SqlValue.Int64).value })
            assertEquals(listOf(100L, 100L), sql.query("SELECT category, COUNT(*) FROM SqlTestModel GROUP BY category ORDER BY category").rows.map { (it[1] as SqlValue.Int64).value })
        } finally { store.close() }
    }

    @Test
    fun topNPreservesStableTiesAndNullOrdering() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store)
            val text = "SELECT column1, column2 FROM (VALUES (1, NULL), (2, 4), (3, 4), (4, 1), (5, NULL)) v ORDER BY column2 DESC NULLS LAST LIMIT 3"
            assertEquals(listOf("2", "3", "4"), sql.query(text).rows.map { it[0].display() })
            assertEquals(SqlValue.Null, sql.query("SELECT SUM(column1) FROM (VALUES (NULL)) v").rows.single()[0])
        } finally { store.close() }
    }
    @Test
    fun upsertExpressionsPreparedWritesAndMetadataShareTheModelContract() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store, options = SqlOptions(allowTableScan = true))
            val prepared = sql.prepareMutation("INSERT INTO SqlTestModel (id, amount) VALUES (?, ?)")
            assertEquals(2, prepared.parameters.size)
            assertEquals(AtomicStatus.Committed, prepared.execute(listOf(SqlValue.UInt64(1u), SqlValue.Exact(Decimal.parse("3.00")))).response.status)
            assertEquals(SqlErrorCode.UNSUPPORTED, assertFailsWith<SqlException> { sql.prepareMutation("INSERT INTO SqlTestModel (id, amount) VALUES (1, 7) ON CONFLICT (id) DO UPDATE SET amount = excluded.__version") }.code)
            val changed = sql.execute("INSERT INTO SqlTestModel (id, amount) VALUES (1, 7) ON CONFLICT (id) DO UPDATE SET amount = amount + excluded.amount RETURNING amount * 2 AS doubled, __version")
            assertEquals("20.00", changed.returning.single().rows.single()[0].display())
            assertEquals(0, sql.execute("INSERT INTO SqlTestModel (id) VALUES (1), (2), (2) ON CONFLICT (id) DO NOTHING").affectedRows - 1)
            assertEquals(2, sql.query("SELECT id FROM SqlTestModel").rows.size)
            assertEquals("SqlTestModel", sql.query("SELECT table_name FROM information_schema.tables").rows.single()[0].display())
            assertEquals(7, sql.query("SELECT column_name FROM information_schema.columns").rows.size)
            assertEquals(1, sql.query("SELECT encoding FROM information_schema.indexes WHERE is_primary").rows.size)
            assertFailsWith<SqlException> { sql.execute("INSERT INTO SqlTestModel (id) VALUES (9) ON CONFLICT (category) DO NOTHING") }
            assertEquals(2, sql.query("SELECT id FROM SqlTestModel").rows.size)
        } finally { store.close() }
    }

    @Test
    fun windowsRespectPartitionsPeersOffsetsFramesAndFinalPagination() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store)
            val source = "(VALUES (1, 'a', 2), (2, 'a', 2), (3, 'a', 5), (4, 'b', 7)) v"
            val rows = sql.query("SELECT column1, ROW_NUMBER() OVER (PARTITION BY column2 ORDER BY column3), RANK() OVER (PARTITION BY column2 ORDER BY column3), DENSE_RANK() OVER (PARTITION BY column2 ORDER BY column3), SUM(column3) OVER (PARTITION BY column2 ORDER BY column3), SUM(column3) OVER (PARTITION BY column2 ORDER BY column3 ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW), LAG(column3, 1, 0) OVER (PARTITION BY column2 ORDER BY column1), LEAD(column3) OVER (PARTITION BY column2 ORDER BY column1) FROM $source ORDER BY column1").rows
            assertEquals(listOf("1", "2", "3", "1"), rows.map { it[1].display() })
            assertEquals(listOf("1", "1", "3", "1"), rows.map { it[2].display() })
            assertEquals(listOf("1", "1", "2", "1"), rows.map { it[3].display() })
            assertEquals(listOf("4", "4", "9", "7"), rows.map { it[4].display() })
            assertEquals(listOf("2", "4", "9", "7"), rows.map { it[5].display() })
            assertEquals(listOf("0", "2", "2", "0"), rows.map { it[6].display() })
            assertEquals(listOf("2", "5", "NULL", "NULL"), rows.map { it[7].display() })
            assertEquals("4", sql.query("SELECT ROW_NUMBER() OVER () FROM $source ORDER BY column1 DESC LIMIT 1").rows.single()[0].display())
            assertEquals("4", sql.query("SELECT COUNT(*) OVER () FROM $source LIMIT 1").rows.single()[0].display())
            assertFailsWith<SqlException> { sql.prepare("SELECT 1 FROM $source WHERE ROW_NUMBER() OVER () = 1") }
            assertFailsWith<SqlException> { sql.query("SELECT LAG(column1, -1) OVER () FROM $source") }
        } finally { store.close() }
    }

    @Test
    fun boundedWindowsHandleEmptyFramesPeersDirectionAndNamedScopes() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store)
            val source = "(VALUES (1), (2), (2), (5), (NULL)) v"
            val rows = sql.query("SELECT column1, SUM(column1) OVER w, FIRST_VALUE(column1) OVER w, LAST_VALUE(column1) OVER w, NTH_VALUE(column1, 2) OVER w FROM $source WINDOW w AS (ORDER BY column1 NULLS LAST ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) ORDER BY column1 NULLS LAST").rows
            assertEquals(listOf("3", "5", "9", "7", "5"), rows.map { it[1].display() })
            assertEquals(listOf("1", "1", "2", "2", "5"), rows.map { it[2].display() })
            assertEquals(listOf("2", "2", "5", "NULL", "NULL"), rows.map { it[3].display() })
            assertEquals(listOf("2", "2", "2", "5", "NULL"), rows.map { it[4].display() })
            assertEquals(listOf("1", "3", "3", "1", "1"), sql.query("SELECT COUNT(*) OVER (ORDER BY column1 RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) FROM $source ORDER BY column1 NULLS LAST").rows.map { it[0].display() })
            assertEquals(listOf("1", "2", "2", "3", "1"), sql.query("SELECT COUNT(*) OVER (ORDER BY column1 DESC NULLS LAST RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) FROM $source ORDER BY column1 DESC NULLS LAST").rows.map { it[0].display() })
            assertEquals(listOf("0", "0", "1", "1", "1"), sql.query("SELECT COUNT(*) OVER (ORDER BY column1 NULLS LAST ROWS BETWEEN 2 PRECEDING AND 2 PRECEDING) FROM $source ORDER BY column1 NULLS LAST").rows.map { it[0].display() })
            assertEquals(listOf("1", "1", "2", "2", "3"), sql.query("SELECT NTILE(3) OVER (ORDER BY column1 NULLS LAST) FROM $source ORDER BY column1 NULLS LAST").rows.map { it[0].display() })
            assertFailsWith<SqlException> { sql.prepare("SELECT COUNT(*) OVER missing FROM $source") }
            assertFailsWith<SqlException> { sql.prepare("SELECT COUNT(*) OVER w FROM $source WINDOW w AS (), w AS ()") }
            assertFailsWith<SqlException> { sql.prepare("SELECT COUNT(*) OVER (ORDER BY column1 ROWS BETWEEN CURRENT ROW AND 1 PRECEDING) FROM $source") }
            assertFailsWith<SqlException> { sql.query("SELECT COUNT(*) OVER (ROWS -1 PRECEDING) FROM $source") }
            assertFailsWith<SqlException> { sql.query("SELECT NTILE(0) OVER () FROM $source") }
        } finally { store.close() }
    }

    @Test
    fun boundedWindowsMatchIndependentNullableFrameCalculations() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store)
            val random = Random(1313)
            repeat(20) {
                val values = List(12) { if (random.nextInt(4) == 0) null else random.nextInt(-10, 11) }
                val before = random.nextInt(5)
                val after = random.nextInt(5)
                val source = values.mapIndexed { index, value -> "($index, ${value ?: "NULL"})" }.joinToString(", ")
                val rows = sql.query("SELECT SUM(column2) OVER w, COUNT(column2) OVER w, MIN(column2) OVER w, MAX(column2) OVER w FROM (VALUES $source) v WINDOW w AS (ORDER BY column1 ROWS BETWEEN $before PRECEDING AND $after FOLLOWING) ORDER BY column1").rows
                for (index in values.indices) {
                    val members = values.subList(maxOf(0, index - before), minOf(values.size, index + after + 1)).filterNotNull()
                    assertEquals(if (members.isEmpty()) SqlValue.Null else SqlValue.Exact(Decimal.parse(members.sum().toString())), rows[index][0])
                    assertEquals(SqlValue.Int64(members.size.toLong()), rows[index][1])
                    assertEquals(members.minOrNull()?.toString() ?: "NULL", rows[index][2].display())
                    assertEquals(members.maxOrNull()?.toString() ?: "NULL", rows[index][3].display())
                }
            }
        } finally { store.close() }
    }

    @Test
    fun floatingRangeOverflowHasAStructuredError() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store)
            val huge = "9".repeat(400)
            val failure = assertFailsWith<SqlException> { sql.query("SELECT COUNT(*) OVER (ORDER BY column1 RANGE $huge PRECEDING) FROM (VALUES (1.0e0)) v") }
            assertEquals(SqlErrorCode.ARITHMETIC, failure.code)
        } finally { store.close() }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun collectionSchemaDepthIsBoundedBeforeReadingValues() {
        var definition = StringDefinition() as IsUsableInMapValue<Any, IsPropertyContext>
        repeat(40) { definition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = definition) as IsUsableInMapValue<Any, IsPropertyContext> }
        assertEquals("collectionDepth", assertFailsWith<SqlLimitException> { collectionColumns(definition) }.limit)
    }

    @Test
    fun correlatedCtesKeepTheEnclosingRow() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store)
            assertEquals(listOf("1", "2"), sql.query("SELECT (WITH c AS (SELECT a.column1 AS value) SELECT value FROM c) FROM (VALUES (1), (2)) a").rows.map { it[0].display() })
        } finally { store.close() }
    }

    @Test
    fun correlatedSetOperandsDoNotCacheOtherRows() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store)
            assertEquals(listOf("1", "2"), sql.query("SELECT (SELECT a.column1 UNION SELECT a.column1) FROM (VALUES (1), (2)) a").rows.map { it[0].display() })
            assertEquals(listOf(SqlValue.Bool(false), SqlValue.Bool(true)), sql.query("SELECT EXISTS (SELECT a.column1 INTERSECT SELECT 2) FROM (VALUES (1), (2)) a").rows.map { it[0] })
            assertEquals(listOf("1", "NULL"), sql.query("SELECT (SELECT a.column1 EXCEPT SELECT 2) FROM (VALUES (1), (2)) a").rows.map { it[0].display() })
        } finally { store.close() }
    }

    @Test
    fun correlatedQueriesRetainOuterProjectionsAndDoNotReuseOtherRowsResults() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store)
            val result = sql.query("SELECT a.column1, (SELECT COUNT(*) FROM (VALUES (1), (2), (2), (NULL)) b WHERE b.column1 = a.column1) FROM (VALUES (1), (2), (3), (NULL)) a ORDER BY a.column1 NULLS LAST")
            assertEquals(listOf("1", "2", "0", "0"), result.rows.map { it[1].display() })
            assertEquals(listOf("1", "2"), sql.query("SELECT a.column1 FROM (VALUES (1), (2), (3)) a WHERE EXISTS (SELECT 1 FROM (VALUES (1), (2)) b WHERE b.column1 = a.column1)").rows.map { it[0].display() })
            assertEquals(listOf("1", "2"), sql.query("SELECT a.column1 FROM (VALUES (1), (2), (3)) a WHERE a.column1 IN (SELECT b.column1 FROM (VALUES (1), (2)) b WHERE b.column1 = a.column1)").rows.map { it[0].display() })
            assertEquals("2", sql.query("SELECT (SELECT (SELECT x.column1) FROM (VALUES (2)) x) FROM (VALUES (1)) x").rows.single()[0].display())
            assertFailsWith<SqlException> { sql.prepare("SELECT (SELECT (SELECT x.column2) FROM (VALUES (2)) x) FROM (VALUES (1, 3)) x") }
            assertEquals("8", sql.query("SELECT (SELECT (SELECT a.column1)) FROM (VALUES (8)) a").rows.single()[0].display())
            assertFailsWith<SqlException> { sql.query("SELECT (SELECT b.column1 FROM (VALUES (1), (1)) b WHERE b.column1 = a.column1) FROM (VALUES (1)) a") }
        } finally { store.close() }
    }

    @Test
    fun collectionParametersExpandWithOrdinalityAndSharedLimits() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store)
            val values = SqlValue.Collection(SqlCollectionKind.LIST, listOf(SqlCollectionEntry(value = SqlValue.Text("first")), SqlCollectionEntry(value = SqlValue.Text("second"))))
            var shared: SqlValue = SqlValue.Int64(1)
            repeat(20) { shared = SqlValue.Collection(SqlCollectionKind.LIST, listOf(SqlCollectionEntry(value = shared), SqlCollectionEntry(value = shared))) }
            assertEquals("collectionValues", assertFailsWith<SqlLimitException> { sql.query("SELECT e.value FROM UNNEST(?) e", listOf(shared)) }.limit)
            var deep: SqlValue = SqlValue.Int64(1)
            repeat(34) { deep = SqlValue.Collection(SqlCollectionKind.LIST, listOf(SqlCollectionEntry(value = deep))) }
            assertEquals("collectionDepth", assertFailsWith<SqlLimitException> { sql.query("SELECT e.value FROM UNNEST(?) e", listOf(deep)) }.limit)
            val nested = SqlValue.Collection(SqlCollectionKind.LIST, listOf(SqlCollectionEntry(value = values)))
            assertEquals(listOf("first", "second"), sql.query("SELECT innerValue.value FROM UNNEST(?) outerValue CROSS JOIN UNNEST(outerValue.value) innerValue", listOf(nested)).rows.map { it[0].display() })
            assertFailsWith<IllegalArgumentException> { SqlValue.Collection(SqlCollectionKind.MAP, listOf(SqlCollectionEntry(value = SqlValue.Text("missing key")))) }
            val result = sql.query("SELECT e.value, e.ordinality FROM UNNEST(?) WITH ORDINALITY e", listOf(values))
            assertEquals(listOf("first", "second"), result.rows.map { it[0].display() })
            assertEquals(listOf("1", "2"), result.rows.map { it[1].display() })
            assertEquals(0, sql.query("SELECT e.value FROM UNNEST(?) e", listOf(SqlValue.Null)).rows.size)
        } finally { store.close() }
    }

    @Test
    fun generatedTopNAndAggregateCasesMatchIndependentReferenceResults() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store)
            val random = Random(47291)
            repeat(40) {
                val values = List(random.nextInt(5, 65)) { random.nextInt(-4, 8) }
                val rows = values.mapIndexed { index, value -> "($index, $value)" }.joinToString()
                val ascending = random.nextBoolean()
                val offset = random.nextInt(0, 4)
                val limit = random.nextInt(1, 5)
                val query = "SELECT column1 FROM (VALUES $rows) v ORDER BY column2 ${if (ascending) "ASC" else "DESC"} LIMIT $limit OFFSET $offset"
                val expected = values.withIndex().let { if (ascending) it.sortedBy { row -> row.value } else it.sortedByDescending { row -> row.value } }.drop(offset).take(limit).map { row -> row.index.toString() }
                assertEquals(expected, sql.query(query).rows.map { row -> row[0].display() })
                val result = sql.query("SELECT COUNT(*), SUM(column2), MIN(column2), MAX(column2) FROM (VALUES $rows) v").rows.single()
                assertEquals(listOf(values.size.toString(), values.sum().toString(), values.min().toString(), values.max().toString()), result.values.map { value -> value.display() })
            }
            assertEquals("99", sql.query("SELECT LAG(NULL, 1, 99) OVER () FROM (VALUES (1)) v").rows.single()[0].display())
            assertTrue(sql.prepare("SELECT column1 FROM (VALUES (3), (1)) v ORDER BY column1 LIMIT 1").explainPlan().operators.any { it.operation == "TOP_N" })
            val queryOnly = MarykSql.create(object : IsDataStore by store {})
            assertFalse(queryOnly.capabilities.embeddedScalarWrites)
            assertFalse(queryOnly.capabilities.scalarReturningExpressions)
            assertFalse(queryOnly.capabilities.transactionalUpsert)
        } finally { store.close() }
    }

    @Test
    fun preparedStreamingOwnsTheScopeUntilCleanupAndTerminalScopesRejectConstants() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val tx = MarykSql.create(store).beginTransaction()
            coroutineScope {
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val read = async { tx.prepare("VALUES (1), (2)").execute().rows.collect { entered.complete(Unit); release.await() } }
                entered.await()
                val commit = async { tx.commit() }
                delay(20)
                assertFalse(commit.isCompleted)
                release.complete(Unit); read.await()
                assertEquals(TransactionStatus.Committed, commit.await().status)
            }
            assertEquals(SqlErrorCode.EXECUTION_STATE, assertFailsWith<SqlException> { tx.execute("UPDATE SqlTestModel SET category = 'closed' WHERE FALSE") }.code)
            assertFailsWith<SqlException> { tx.query("SELECT 1") }
            assertFailsWith<SqlException> { tx.prepare("SELECT 1").execute().rows.collect {} }
        } finally { store.close() }
    }

    @Test
    fun recursiveEmbeddedModelsLeaveOtherFieldsQueryable() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to TestMarykModel))
        try {
            val sql = MarykSql.create(store)
            assertEquals(SqlValue.Int64(1), sql.query("SELECT 1").rows.single()[0])
            sql.prepare("SELECT string FROM TestMarykModel")
            assertTrue(sql.catalog.tables.single().columns.any { it.type == SqlType.ANY })
        } finally { store.close() }
    }

    @Test
    fun fittingJoinRowsCanFallBackWhenTheHashDoesNotFit() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val sql = MarykSql.create(store, options = SqlOptions(maxBufferedBytes = 10000))
            val right = (1..30).joinToString { "($it)" }
            assertEquals("1", sql.query("SELECT a.column1 FROM (VALUES (1)) a JOIN (VALUES $right) b ON a.column1 = b.column1").rows.single()[0].display())
        } finally { store.close() }
    }

}
