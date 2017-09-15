package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test
import kotlin.test.assertEquals

internal class ByteKtTest {
    private val bytesToTest = byteArrayOf(
            -1,
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

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        bytesToTest.forEach {
            bc.reserve(1)
            it.writeBytes(bc::write)

            initByte(bc::read) shouldBe it
            bc.reset()
        }
    }
}