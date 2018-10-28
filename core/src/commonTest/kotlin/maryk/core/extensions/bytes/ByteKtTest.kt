package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

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
    fun testZigZagAndBack() {
        bytesToTest.forEach {
            it.encodeZigZag().decodeZigZag() shouldBe it
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

    @Test
    fun testStreamingVarIntZigZagConversion() {
        fun testZigZagByteContent(bc: ByteCollector, it: Byte, hexValue: String) =
            this.testByteContent(bc, it.encodeZigZag(), hexValue)

        val bc = ByteCollector()

        testZigZagByteContent(bc, 22, "2c")
        testZigZagByteContent(bc, -22, "2b")
        testZigZagByteContent(bc, 1, "02")
        testZigZagByteContent(bc, 0, "00")
        testZigZagByteContent(bc, -1, "01")
        testZigZagByteContent(bc, Byte.MAX_VALUE, "fe01")
        testZigZagByteContent(bc, Byte.MIN_VALUE, "ff01")
    }

    private fun testByteContent(bc: ByteCollector, it: Byte, hexValue: String) {
        bc.reserve(it.calculateVarByteLength())
        it.writeVarBytes(bc::write)
        initByteByVar(bc::read) shouldBe it

        bc.bytes!!.toHex() shouldBe hexValue
        bc.reset()
    }

    @Test
    fun testWrongVarInt() {
        val bytes = ByteArray(3, { -1 })
        var index = 0
        shouldThrow<ParseException> {
            initByteByVar { bytes[index++] }
        }
    }
}
