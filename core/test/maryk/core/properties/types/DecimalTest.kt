package maryk.core.properties.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DecimalTest {
    @Test
    fun convertsKotlinIntegerValuesExactly() {
        assertEquals("-128", (-128).toByte().toDecimal().toString())
        assertEquals("32767", Short.MAX_VALUE.toDecimal().toString())
        assertEquals("12.00", 12.toDecimal(2u).toString())
        assertEquals("-12.00", (-12L).toDecimal(2u).toString())
        assertEquals("255", UByte.MAX_VALUE.toDecimal().toString())
        assertEquals("65535", UShort.MAX_VALUE.toDecimal().toString())
        assertEquals("255", 255u.toDecimal().toString())
        assertEquals("18446744073709551615", ULong.MAX_VALUE.toDecimal().toString())
        assertEquals("-1234567890123456789012345678.90", Decimal.fromUnscaled("-123456789012345678901234567890", 2u).toString())
    }

    @Test
    fun parsesAndPreservesFixedPointScale() {
        assertEquals("12.30", Decimal.parse("12.30").toString())
        assertEquals("-0.005", Decimal.parse("-0.005").toString())
        assertEquals("42", Decimal.parse("+42").toString())
        assertEquals(2u, Decimal.parse("12.30").scale)
        assertEquals(1230L, Decimal.parse("12.30").unscaledValue)
    }

    @Test
    fun rescalesOnlyWithoutPrecisionLoss() {
        assertEquals(Decimal.fromUnscaled(12_300L, 3u), Decimal.parse("12.3").rescaleExact(3u))
        assertEquals(Decimal.fromUnscaled(123L, 1u), Decimal.parse("12.30").rescaleExact(1u))
        assertFailsWith<ArithmeticException> {
            Decimal.parse("12.34").rescaleExact(1u)
        }
        assertEquals("9223372036854775807.0", Decimal.fromUnscaled(Long.MAX_VALUE, 0u).rescaleExact(1u).toString())
    }

    @Test
    fun comparesExactlyAcrossScalesAndLargeValues() {
        assertEquals(0, Decimal.parse("12.3").compareTo(Decimal.parse("12.300")))
        assertTrue(Decimal.parse("9223372036854775807") > Decimal.parse("922337203685477580.6"))
        assertTrue(Decimal.parse("-0.000000000000000002") < Decimal.parse("-0.000000000000000001"))
    }

    @Test
    fun calculatesExactValuesBeyondLongRange() {
        val value = Decimal.parse("999999999999999999999999.99")

        assertEquals("1000000000000000000000000.00", (value + Decimal.parse("0.01")).toString())
        assertEquals("999999999999999999999999.98", (value - Decimal.parse("0.01")).toString())
        assertEquals("9999999999999999999999999.90", (value * Decimal.parse("10")).toString())
        assertEquals("249999999999999999999999.9975", value.divideExact(Decimal.parse("4"), 4u).toString())
        assertEquals("25.00", (Decimal.parse("100.00") / Decimal.parse("4")).toString())
    }

    @Test
    fun dividesSignedValuesOnlyWhenTheResultIsExact() {
        assertEquals("-25.00", (Decimal.parse("100.00") / Decimal.parse("-4")).toString())
        assertEquals("25.00", Decimal.parse("-100.00").divideExact(Decimal.parse("-4")).toString())
        assertFailsWith<ArithmeticException> { Decimal.parse("1") / Decimal.parse("3") }
        assertFailsWith<IllegalArgumentException> { Decimal.parse("1") / Decimal.parse("0") }
    }

    @Test
    fun preservesScaleAndSignsAcrossArithmetic() {
        assertEquals("1.23", (Decimal.parse("1.2") + Decimal.parse("0.03")).toString())
        assertEquals("-2.4", (Decimal.parse("1.2") * Decimal.parse("-2")).toString())
        assertEquals("0", Decimal.parse("-0").toString())
        assertFailsWith<IllegalArgumentException> {
            Decimal.parse("1.000000000000000000") * Decimal.parse("1.0")
        }
    }

    @Test
    fun rejectsLongCompatibilityAccessForArbitrarySizeValues() {
        assertFailsWith<IllegalArgumentException> {
            Decimal.parse("9223372036854775808").unscaledValue
        }
    }

    @Test
    fun rejectsInvalidValues() {
        listOf("", ".", "1.", ".1", "1e2", "NaN", "Infinity").forEach {
            assertFailsWith<IllegalArgumentException>(it) { Decimal.parse(it) }
        }
        assertFailsWith<IllegalArgumentException> {
            Decimal.parse("0.0000000000000000001")
        }
        assertEquals("9223372036854775808", Decimal.parse("9223372036854775808").toString())
        assertFailsWith<IllegalArgumentException> {
            Decimal.fromUnscaled(1L, 19u)
        }
    }
}
