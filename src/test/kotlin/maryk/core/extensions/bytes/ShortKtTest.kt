package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollector
import org.junit.Test

internal class ShortKtTest {
    private val shortsToTest = shortArrayOf(
            Short.MIN_VALUE,
            -2222,
            2222,
            0,
            Short.MAX_VALUE
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

    @Test
    fun testZigZagAndBack() {
        shortsToTest.forEach {
            it.encodeZigZag().decodeZigZag() shouldBe it
        }
    }

    @Test
    fun testStreamingVarIntConversion() {
        val bc = ByteCollector()
        testByteContent(bc, 2222, "ae11")
        testByteContent(bc, -2222, "d2ee03")
        testByteContent(bc, 1, "01")
        testByteContent(bc, 0, "00")
        testByteContent(bc, -1, "ffff03")
        testByteContent(bc, Short.MAX_VALUE, "ffff01")
        testByteContent(bc, Short.MIN_VALUE, "808002")
    }

    @Test
    fun testStreamingVarIntZigZagConversion() {
        val bc = ByteCollector()
        testZigZagByteContent(bc, 2222, "dc22")
        testZigZagByteContent(bc, -2222, "db22")
        testZigZagByteContent(bc, 22, "2c")
        testZigZagByteContent(bc, -22, "2b")
        testZigZagByteContent(bc, 1, "02")
        testZigZagByteContent(bc, 0, "00")
        testZigZagByteContent(bc, -1, "01")
        testZigZagByteContent(bc, Short.MAX_VALUE, "feff03")
        testZigZagByteContent(bc, Short.MIN_VALUE, "ffff03")
    }

    private fun testZigZagByteContent(bc: ByteCollector, it: Short, hexValue: String) {
        this.testByteContent(bc, it.encodeZigZag(), hexValue)
    }

    private fun testByteContent(bc: ByteCollector, it: Short, hexValue: String) {
        bc.reserve(it.computeVarByteSize())
        it.writeVarBytes(bc::write)
        initShortByVar(bc::read) shouldBe it

        bc.bytes!!.toHex() shouldBe hexValue

        bc.reset()
    }
}