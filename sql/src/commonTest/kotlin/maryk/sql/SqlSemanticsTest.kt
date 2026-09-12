package maryk.sql

import maryk.core.properties.types.Decimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlSemanticsTest {
    @Test
    fun nullUsesThreeValuedTruthTables() {
        val t = SqlValue.Bool(true)
        val f = SqlValue.Bool(false)
        val n = SqlValue.Null
        assertEquals(n, sqlAnd(t, n))
        assertEquals(f, sqlAnd(f, n))
        assertEquals(t, sqlOr(t, n))
        assertEquals(n, sqlOr(f, n))
        assertEquals(n, sqlNot(n))
        assertNull(sqlCompare(n, n))
        assertFailsWith<SqlException> { sqlAnd(SqlValue.Int64(1), t) }
    }

    @Test
    fun numericEqualityAndGroupingRemainExact() {
        val large = SqlValue.UInt64(ULong.MAX_VALUE)
        assertTrue(sqlCompare(large, SqlValue.Int64(Long.MAX_VALUE))!! > 0)
        val one = SqlValue.Int64(1)
        val scaled = SqlValue.Exact(Decimal.parse("1.000"))
        assertEquals(0, sqlCompare(one, scaled))
        assertEquals(sqlKey(one), sqlKey(scaled))
        assertEquals(sqlKey(SqlValue.Exact(Decimal.parse("-0.00"))), sqlKey(SqlValue.UInt64(0u)))
        assertFailsWith<SqlException> { sqlCompare(SqlValue.Text("1"), one) }
    }
}
