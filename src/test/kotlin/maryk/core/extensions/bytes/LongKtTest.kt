package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import org.junit.Test

internal class LongKtTest {
    private val longsToTest = longArrayOf(
            Long.MIN_VALUE,
            -4786131286145765123,
            -2222,
            -1,
            0,
            1,
            2222,
            4786131286145765123,
            Long.MAX_VALUE
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        longsToTest.forEach {
            bc.reserve(8)
            it.writeBytes(bc::write, 8)

            initLong(bc::read, 8) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStreaming7Conversion() {
        val bc = ByteCollector()
        longArrayOf(
                MIN_SEVEN_VALUE,
                -999999999L,
                -1L,
                0L,
                1L,
                1504201744L,
                999999999,
                MAX_SEVEN_VALUE
        ).forEach {
            bc.reserve(7)
            it.writeBytes(bc::write, 7)

            initLong(bc::read, 7) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testOutOfRangeConversion() {
        shouldThrow<IllegalArgumentException> {
            4L.writeBytes({}, 9)
        }
    }
}