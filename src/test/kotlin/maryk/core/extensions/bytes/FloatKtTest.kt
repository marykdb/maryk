package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test

internal class FloatKtTest {
    private val floatsToTest = floatArrayOf(
            Float.NEGATIVE_INFINITY,
            -2222.22F,
            -1F
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
        floatsToTest.forEach {
            bc.reserve(4)
            it.writeBytes(bc::write)

            initFloat(bc::read) shouldBe it
            bc.reset()
        }
    }
}