package maryk.core.properties.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DecimalRoundingTest {
    @Test
    fun roundedMultiplicationHandlesIntermediateScaleAboveMaximum() {
        assertEquals("1.00", Decimal.parse("1.005").multiplyRounded(Decimal.parse("1"), 2u).toString())
        assertEquals("-1.02", Decimal.parse("-1.015").multiplyRounded(Decimal.parse("1"), 2u).toString())
        assertEquals("0.000000000000000000", Decimal.parse("0.000000000000000001").multiplyRounded(Decimal.parse("0.000000000000000001"), 18u).toString())
        assertEquals("0.000000000000000001", Decimal.parse("0.000000000000000001").multiplyRounded(Decimal.parse("0.600000000000000000"), 18u).toString())
        assertEquals("123456789012345678901234567890.000", Decimal.parse("12345678901234567890123456789").multiplyRounded(Decimal.parse("10"), 3u).toString())
        assertFailsWith<IllegalArgumentException> { Decimal.parse("1").multiplyRounded(Decimal.parse("1"), 19u) }
    }

    @Test
    fun remainderTruncatesQuotientTowardsZeroAndPreservesCommonScale() {
        for ((left, right, expected) in listOf(
            Triple("10.50", "3", "1.50"),
            Triple("-10.50", "3", "-1.50"),
            Triple("10.50", "-3", "1.50"),
            Triple("-10.50", "-3", "-1.50"),
            Triple("1", "0.03", "0.01"),
            Triple("1000000000000000000000000000000", "3", "1"),
        )) assertEquals(expected, (Decimal.parse(left) % Decimal.parse(right)).toString())
        assertFailsWith<IllegalArgumentException> { Decimal.parse("1") % Decimal.parse("0") }
    }

    @Test
    fun divisionRoundsTiesToEvenIncludingNegativeAndZeroQuotients() {
        val two = Decimal.parse("2")
        for ((value, expected) in listOf("1" to "0", "3" to "2", "5" to "2", "-1" to "0", "-3" to "-2")) {
            assertEquals(expected, Decimal.parse(value).divideRounded(two, 0u).toString())
        }
        assertEquals("-0.666667", Decimal.parse("2").divideRounded(Decimal.parse("-3"), 6u).toString())
        assertEquals("333333333333333333333333.333333", Decimal.parse("1000000000000000000000000").divideRounded(Decimal.parse("3"), 6u).toString())
    }

    @Test
    fun rescaleIsExactWhenGrowingAndHalfEvenWhenShrinking() {
        assertEquals("12.3400", Decimal.parse("12.34").rescaleRounded(4u).toString())
        assertEquals("1.24", Decimal.parse("1.245").rescaleRounded(2u).toString())
        assertEquals("-1.26", Decimal.parse("-1.255").rescaleRounded(2u).toString())
        assertEquals("0", Decimal.parse("-0.4").rescaleRounded(0u).toString())
        assertEquals("10.00", Decimal.parse("9.999").rescaleRounded(2u).toString())
    }

    @Test
    fun exactOperationsStillRejectRoundingAndInvalidInputsFail() {
        assertFailsWith<ArithmeticException> { Decimal.parse("1").divideExact(Decimal.parse("3")) }
        assertFailsWith<IllegalArgumentException> { Decimal.parse("1").divideRounded(Decimal.parse("0"), 2u) }
        assertFailsWith<IllegalArgumentException> { Decimal.parse("1").rescaleRounded(19u) }
    }
}
