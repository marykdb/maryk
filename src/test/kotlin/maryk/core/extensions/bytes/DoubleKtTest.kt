package maryk.core.extensions.bytes

import org.junit.Test
import kotlin.test.assertEquals

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
    fun testConversion() {
        doublesToTest.forEach {
            assertEquals(
                    it,
                    initDouble(it.toBytes())
            )
        }
    }

    @Test
    fun testOffsetConversion() {
        doublesToTest.forEach {
            val bytes = ByteArray(22)
            assertEquals(
                    it,
                    initDouble(it.toBytes(bytes, 10), 10)
            )
        }
    }
}