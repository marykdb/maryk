package maryk.core.extensions.bytes

import maryk.core.properties.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

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
        doublesToTest.forEach {
            bc.reserve(8)
            it.writeBytes(bc::write)

            initDouble(bc::read) shouldBe it
            bc.reset()
        }
    }
}