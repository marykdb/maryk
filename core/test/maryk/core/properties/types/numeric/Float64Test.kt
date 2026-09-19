package maryk.core.properties.types.numeric

import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class Float64Test {
    private val float64values = doubleArrayOf(
        Double.NEGATIVE_INFINITY,
        Double.MIN_VALUE,
        -626786234.3234742349,
        0.0,
        6267862343.234742349,
        Double.MAX_VALUE,
        Double.POSITIVE_INFINITY,
        Double.NaN
    )

    @Test
    fun testRandom() {
        Float64.createRandom()
    }

    @Test
    fun testStringConversion() {
        for (double in float64values) {
            expect(double) { Float64.ofString(double.toString()) }
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        for (double in float64values) {
            bc.reserve(Float64.size)
            Float64.writeStorageBytes(double, bc::write)
            expect(double) { Float64.fromStorageByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        for (double in float64values) {
            bc.reserve(Float64.calculateTransportByteLength(double))
            Float64.writeTransportBytes(double, bc::write)
            val value = Float64.readTransportBytes(bc::read)
            expect(double) { value }
            bc.reset()
        }
    }

    @Test
    fun testOfNativeTypes() {
        expect(21312321.00) { Float64.ofLong(21312321) }
        expect(1221321.12131657) { Float64.ofDouble(1221321.12131657) }
        expect(1221321.0) { Float64.ofInt(1221321) }
    }

    @Test
    fun testIsOfType() {
        assertTrue { Float64.isOfType(22.02) }
        assertFalse { Float64.isOfType(24L) }
    }
}
