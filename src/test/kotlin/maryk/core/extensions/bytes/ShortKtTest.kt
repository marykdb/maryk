package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test
import kotlin.test.assertEquals

internal class ShortKtTest {
    private val shortsToTest = shortArrayOf(
            2222,
            -2222,
            0,
            Short.MAX_VALUE,
            Short.MIN_VALUE
    )

    @Test
    fun testConversion() {
        shortsToTest.forEach {
            assertEquals(
                    it,
                    initShort(it.toBytes())
            )
        }
    }

    @Test
    fun testOffsetConversion() {
        shortsToTest.forEach {
            val bytes = ByteArray(22)
            assertEquals(
                    it,
                    initShort(it.toBytes(bytes, 10), 10)
            )
        }
    }

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