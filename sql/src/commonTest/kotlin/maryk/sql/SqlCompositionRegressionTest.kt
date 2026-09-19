package maryk.sql

import kotlinx.coroutines.flow.toList
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.number
import maryk.core.properties.types.Decimal
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.UInt64
import maryk.datastore.memory.InMemoryDataStore
import maryk.test.models.Option
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object SqlCompositionItem : RootDataModel<SqlCompositionItem>(
    keyDefinition = { SqlCompositionItem.id.ref() },
    indexes = { listOf(SqlCompositionItem.value.ref()) },
) {
    val id by number(1u, UInt64, final = true)
    val value by number(2u, Float64)
    val kind by enum(3u, enum = Option, required = false)
}

class SqlCompositionRegressionTest {
    private suspend fun withSql(options: SqlOptions = SqlOptions(allowTableScan = true), block: suspend (MarykSql) -> Unit) {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlCompositionItem))
        try { block(MarykSql.create(store, options = options)) }
        finally { store.close() }
    }

    @Test fun scalarConversionPreservesNumericValues() {
        assertEquals(SqlValue.Float64(2.5), SqlValue.of(2.5))
        assertEquals(SqlValue.Float64(2.0), SqlValue.of(2.0))
        assertEquals(SqlValue.Float64(2.5), SqlValue.of(2.5f))
        assertEquals(SqlValue.Int64(Int.MAX_VALUE.toLong()), SqlValue.of(Int.MAX_VALUE))
        assertEquals(SqlValue.Int64(Long.MAX_VALUE), SqlValue.of(Long.MAX_VALUE))
        for (value: Any in listOf(2.5, 4_000_000_000.0)) {
            assertEquals(SqlValue.Float64((value as Number).toDouble()), SqlValue.of(value))
        }
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { SqlValue.of(value) }
            val erased: Any = value
            assertFailsWith<IllegalArgumentException> { SqlValue.of(erased) }
        }
    }

    @Test fun signedZeroNativeFiltersAgreeWithResiduals() = sqlTest { withSql { sql ->
        sql.execute("INSERT INTO SqlCompositionItem (id, value) VALUES (1, -0.0e0), (2, 0.0e0), (3, -1.0e0), (4, 1.0e0)")
        for (operator in listOf("=", "<", "<=", ">", ">=")) for (zero in listOf("0.0e0", "-0.0e0")) {
            val residual = sql.query("SELECT id FROM SqlCompositionItem WHERE value + 0.0e0 $operator $zero ORDER BY id").rows.map { it[0] }
            assertEquals(residual, sql.query("SELECT id FROM SqlCompositionItem WHERE value $operator $zero ORDER BY id").rows.map { it[0] }, "$operator $zero")
        }
    } }

    @Test fun floatingParametersFollowNumericContext() = sqlTest { withSql { sql ->
        for (statement in listOf("SELECT ? + 1.0e0", "SELECT 1.0e0 + ?", "SELECT ABS(?) + 1.0e0")) {
            assertEquals(SqlValue.Float64(3.0), sql.query(statement, listOf(SqlValue.Float64(2.0))).rows.single()[0])
        }
        val abs = sql.prepare("SELECT ABS(?)")
        assertEquals(SqlValue.Float64(2.0), abs.execute(listOf(SqlValue.Float64(-2.0))).rows.toList().single()[0])
        assertEquals(SqlValue.Exact(Decimal.parse("2")), abs.execute(listOf(SqlValue.Int64(-2))).rows.toList().single()[0])
        assertEquals(SqlValue.Float64(2.0), sql.query("SELECT -?", listOf(SqlValue.Float64(-2.0))).rows.single()[0])
        assertEquals(SqlValue.Float64(2.5), sql.query("SELECT SUM(?)", listOf(SqlValue.Float64(2.5))).rows.single()[0])
        assertEquals(SqlErrorCode.PARAMETER, assertFailsWith<SqlException> { abs.execute(listOf(SqlValue.Text("wrong"))) }.code)
        assertFailsWith<SqlException> { sql.query("SELECT ? + 1.0e0", listOf(SqlValue.Int64(2))) }
    } }

    @Test fun ordinalsIdentifySlotsDespiteDuplicateLabels() = sqlTest { withSql { sql ->
        val query = "SELECT 2 AS x, 3 AS x UNION ALL SELECT 1, 4 ORDER BY 1"
        assertEquals(listOf(SqlValue.Int64(1), SqlValue.Int64(2)), sql.query(query).rows.map { it[0] })
        assertEquals(listOf(SqlValue.Int64(3), SqlValue.Int64(4)), sql.query(query.replace("ORDER BY 1", "ORDER BY 2")).rows.map { it[1] })
        assertEquals(SqlValue.Int64(2), sql.query("(SELECT 2 AS x, 3 AS x) ORDER BY 1").rows.single()[0])
        assertFailsWith<SqlException> { sql.prepare(query.replace("ORDER BY 1", "ORDER BY x")) }
    } }

    @Test fun existsIgnoresUnusedProjectionsButPreservesCardinality() = sqlTest { withSql { sql ->
        val cases = mapOf(
            "SELECT 1 / 0" to true,
            "SELECT (SELECT 1 / 0)" to true,
            "SELECT 1 ORDER BY 1 / 0" to true,
            "SELECT 1 / 0 WHERE FALSE" to false,
            "SELECT COUNT(*) WHERE FALSE" to true,
            "SELECT COUNT(*) WHERE FALSE HAVING COUNT(*) > 0" to false,
            "SELECT 1 / 0 LIMIT 0" to false,
            "VALUES (1 / 0), (2 / 0) OFFSET 1" to true,
            "SELECT DISTINCT column1 FROM (VALUES (1), (1)) t OFFSET 1" to false,
            "SELECT DISTINCT column1 FROM (VALUES (1), (2)) t OFFSET 1" to true,
            "SELECT 1 / 0 UNION ALL SELECT 2 / 0 OFFSET 1" to true,
            "VALUES (1) INTERSECT VALUES (2)" to false,
            "VALUES (1) EXCEPT VALUES (1)" to false,
            "VALUES (1) UNION VALUES (1) OFFSET 1" to false,
        )
        for ((query, expected) in cases) assertEquals(SqlValue.Bool(expected), sql.query("SELECT EXISTS ($query)").rows.single()[0], query)
    } }

    @Test fun existsStopsAfterTheFirstMatchingRow() = sqlTest {
        withSql(SqlOptions(allowTableScan = true, pageSize = 1, maxFetchedRows = 1)) { sql ->
            sql.execute("INSERT INTO SqlCompositionItem (id, value) VALUES (1, 1.0e0), (2, 2.0e0)")
            val execution = sql.prepare("SELECT EXISTS (SELECT value / 0.0e0 FROM SqlCompositionItem)").execute()
            assertEquals(SqlValue.Bool(true), execution.rows.toList().single()[0])
            assertEquals(1, execution.awaitCompletion().storeRequests)
        }
    }

    @Test fun conditionalParametersKeepTheirModelAndEnumDomains() = sqlTest { withSql { sql ->
        sql.execute("INSERT INTO SqlCompositionItem (id, value, kind) VALUES (1, 2.0e0, 'V1')")
        val key = sql.query("SELECT __key FROM SqlCompositionItem").rows.single()[0] as SqlValue.Key
        for (expression in listOf("CASE WHEN TRUE THEN ? ELSE __key END", "COALESCE(?, __key)", "NULLIF(?, __key)")) {
            val prepared = sql.prepare("SELECT $expression FROM SqlCompositionItem")
            val expected = if (expression.startsWith("NULLIF")) SqlValue.Null else key
            assertEquals(expected, prepared.execute(listOf(SqlValue.Text(key.value.toString()))).rows.toList().single()[0])
            assertEquals(SqlErrorCode.PARAMETER, assertFailsWith<SqlException> {
                prepared.execute(listOf(SqlValue.Key("WrongModel", key.value)))
            }.code)
        }
        val enum = sql.prepare("SELECT CASE WHEN TRUE THEN ? ELSE kind END FROM SqlCompositionItem")
        assertEquals(SqlValue.Enum("Option", 2u, "V2"), enum.execute(listOf(SqlValue.Text("V2"))).rows.toList().single()[0])
        assertFailsWith<SqlException> { enum.execute(listOf(SqlValue.Enum("Option", 1u, "V2"))) }
    } }
}
