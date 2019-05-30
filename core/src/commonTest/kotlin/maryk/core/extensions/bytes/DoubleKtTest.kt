package maryk.core.extensions.bytes

import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.expect

internal class DoubleKtTest {
    private val doublesToTest = doubleArrayOf(
        Double.NEGATIVE_INFINITY,
        -2222.231414124122,
        -1.0,
        -0.0,
        0.0,
        Double.MIN_VALUE,
        1.0,
        2.0,
        2222.2124124124142,
        Double.MAX_VALUE,
        Double.POSITIVE_INFINITY,
        Double.NaN
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        doublesToTest.forEach { double ->
            bc.reserve(8)
            double.writeBytes(bc::write)

            expect(double) { initDouble(bc::read) }
            bc.reset()
        }
    }
}
