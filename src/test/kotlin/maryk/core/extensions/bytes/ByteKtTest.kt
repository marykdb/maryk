package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test

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
    fun testStreamingConversion() {
        val bc = ByteCollector()
        bytesToTest.forEach {
            bc.reserve(1)
            it.writeBytes(bc::write)

            initByte(bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStreamingVarIntConversion() {
        val bc = ByteCollector()

        testByteContent(bc, 22, "16")
        testByteContent(bc, -22, "ea01")
        testByteContent(bc, 1, "01")
        testByteContent(bc, 0, "00")
        testByteContent(bc, -1, "ff01")
        testByteContent(bc, Byte.MAX_VALUE, "7f")
        testByteContent(bc, Byte.MIN_VALUE, "8001")
    }

    private fun testByteContent(bc: ByteCollector, it: Byte, hexValue: String) {
        bc.reserve(it.computeVarByteSize())
        it.writeVarBytes(bc::write)
        initByteByVar(bc::read) shouldBe it

        bc.bytes!!.toHex() shouldBe hexValue
        bc.reset()
    }
}