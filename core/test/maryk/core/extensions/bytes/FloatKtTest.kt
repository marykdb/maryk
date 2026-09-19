package maryk.core.extensions.bytes

import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.expect

internal class FloatKtTest {
    private val floatsToTest = floatArrayOf(
        Float.NEGATIVE_INFINITY,
        -2222.22F,
        -1F,
        -0F,
        0F,
        Float.MIN_VALUE,
        1F,
        2222.22F,
        Float.MAX_VALUE,
        Float.POSITIVE_INFINITY
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        floatsToTest.forEach { float ->
            bc.reserve(4)
            float.writeBytes(bc::write)

            expect(float) { initFloat(bc::read) }
            bc.reset()
        }
    }
}
