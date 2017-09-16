package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test

internal class ShortKtTest {
    private val shortsToTest = shortArrayOf(
            2222,
            -2222,
            0,
            Short.MAX_VALUE,
            Short.MIN_VALUE
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        shortsToTest.forEach {
            bc.reserve(2)
            it.writeBytes(bc::write)

            initShort(bc::read) shouldBe it
            bc.reset()
        }
    }
}