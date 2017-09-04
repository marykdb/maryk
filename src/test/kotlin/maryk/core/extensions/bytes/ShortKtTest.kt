package maryk.core.extensions.bytes

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
}