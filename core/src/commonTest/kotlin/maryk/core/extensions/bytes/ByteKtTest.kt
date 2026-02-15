package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

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
        bytesToTest.forEach { byte ->
            bc.reserve(1)
            byte.writeBytes(bc::write)

            expect(byte) { initByte(bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testZigZagAndBack() {
        bytesToTest.forEach { byte ->
            expect(byte) { byte.encodeZigZag().decodeZigZag() }
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

    private fun testByteContent(bc: ByteCollector, byte: Byte, hexValue: String) {
        bc.reserve(byte.calculateVarByteLength())
        byte.writeVarBytes(bc::write)
        expect(byte) { initByteByVar(bc::read) }

        expect(hexValue) { bc.bytes!!.toHexString() }
        bc.reset()
    }

    @Test
    fun testWrongVarInt() {
        val bytes = ByteArray(3) { -1 }
        var index = 0
        assertFailsWith<ParseException> {
            initByteByVar { bytes[index++] }
        }
    }
}
