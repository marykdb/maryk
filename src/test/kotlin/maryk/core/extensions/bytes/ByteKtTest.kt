package maryk.core.extensions.bytes

import org.junit.Test
import kotlin.test.assertEquals

internal class ByteKtTest {
    private val bytesToTest = byteArrayOf(
            22,
            -22,
            0,
            Byte.MAX_VALUE,
            Byte.MIN_VALUE
    )

    @Test
    fun testConversion() {
        bytesToTest.forEach {
            assertEquals(
                    it,
                    initByte(it.toBytes())
            )
        }
    }

    @Test
    fun testOffsetConversion() {
        bytesToTest.forEach {
            val bytes = ByteArray(22)
            assertEquals(
                    it,
                    initByte(it.toBytes(bytes, 10), 10)
            )
        }
    }
}