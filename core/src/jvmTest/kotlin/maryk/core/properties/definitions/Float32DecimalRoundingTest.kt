@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") // The JDK parser is the independent oracle.

package maryk.core.properties.definitions

import java.lang.Float as JavaFloat
import java.math.BigDecimal
import maryk.core.properties.types.numeric.Float32
import kotlin.test.Test
import kotlin.test.assertEquals

class Float32DecimalRoundingTest {
    @Test
    fun decimalMidpointsAgreeWithTheIndependentJdkFloatParser() {
        val definition = NumberDefinition(type = Float32)
        val boundaries = buildList {
            addAll(listOf(0, 1, 2, 0x007fffff, 0x00800000, 0x3f7fffff, 0x3f800000, 0x3f800001, 0x7f7ffffe))
            var state = 0x4d415259
            repeat(256) {
                state = state xor (state shl 13)
                state = state xor (state ushr 17)
                state = state xor (state shl 5)
                add((state and Int.MAX_VALUE) % 0x7f7fffff)
            }
        }
        for (bits in boundaries) {
            val lower = BigDecimal(Float.fromBits(bits).toDouble())
            val upper = BigDecimal(Float.fromBits(bits + 1).toDouble())
            val midpoint = lower.add(upper).divide(BigDecimal("2"))
            val epsilon = BigDecimal.ONE.scaleByPowerOfTen(-maxOf(midpoint.scale() + 4, 170))
            for (value in listOf(midpoint.subtract(epsilon), midpoint, midpoint.add(epsilon))) {
                for (text in listOf(value.toPlainString(), value.negate().toPlainString(), value.toString())) {
                    assertEquals(JavaFloat.parseFloat(text).toRawBits(), definition.fromString(text).toRawBits(), text)
                }
            }
        }
        for (text in listOf("0x1.000001000000000001p0", "0x1.000002fffffffffffep0", "0x1p-150",
            "0x1.000000000000001p-150", "0x1.ffffffp127", "0x1.fffffefffffffffffep127")) {
            assertEquals(JavaFloat.parseFloat(text).toRawBits(), definition.fromString(text).toRawBits(), text)
        }
    }

    @Test
    fun signedLongConversionsAgreeWithDirectJdkRounding() {
        val values = buildList {
            addAll(listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0, 1, -1, (1L shl 62) + (1L shl 38) + 1))
            for (exponent in 24..62) {
                val base = 1L shl exponent
                val half = 1L shl (exponent - 24)
                for (offset in listOf(-1L, 0L, 1L)) {
                    val evenBoundary = base + half + offset
                    val oddBoundary = base + 3 * half + offset
                    addAll(listOf(evenBoundary, -evenBoundary, oddBoundary, -oddBoundary))
                }
            }
            var state = 0x4d4152594bL
            repeat(512) {
                state = state xor (state shl 13)
                state = state xor (state ushr 7)
                state = state xor (state shl 17)
                add(state)
            }
        }
        for (value in values) assertEquals(value.toFloat().toRawBits(), Float32.ofLong(value).toRawBits(), value.toString())
    }
}
