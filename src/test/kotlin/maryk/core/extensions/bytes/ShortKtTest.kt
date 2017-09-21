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

    private fun testByteContent(bc: ByteCollector, it: Short, hexValue: String) {
        bc.reserve(it.computeVarByteSize())
        it.writeVarBytes(bc::write)
        initShortByVar(bc::read) shouldBe it

        bc.bytes!!.toHex() shouldBe hexValue

        bc.reset()
    }
}