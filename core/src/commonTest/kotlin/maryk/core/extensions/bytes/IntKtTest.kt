package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class IntKtTest {
    private val intsToTest = intArrayOf(
        Int.MIN_VALUE,
        -1933587636,
        -2222,
        -1,
        0,
        1,
        2222,
        923587636,
        Int.MAX_VALUE
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        intsToTest.forEach { int ->
            bc.reserve(4)
            int.writeBytes(bc::write)

            expect(int) { initInt(bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testStreaming3Conversion() {
        val bc = ByteCollector()
        intArrayOf(
            -0x800000,
            -0x7FFFFF,
            -1,
            0,
            1,
            2222,
            0x7FFFFF
        ).forEach { int ->
            bc.reserve(3)
            int.writeBytes(bc::write, 3)

            expect(int) { initInt(bc::read, 3) }
            bc.reset()
        }
    }

    @Test
    fun testOutOfRangeConversion() {
        assertFailsWith<IllegalArgumentException> {
            4.writeBytes({}, 5)
        }
    }

    @Test
    fun testZigZagAndBack() {
        intsToTest.forEach { int ->
            expect(int) { int.encodeZigZag().decodeZigZag() }
        }
    }

    @Test
    fun testStreamingVarIntConversion() {
        val bc = ByteCollector()

        testByteContent(bc, 2222, "ae11")
        testByteContent(bc, -2222, "d2eeffff0f")
        testByteContent(bc, 1, "01")
        testByteContent(bc, 0, "00")
        testByteContent(bc, -1, "ffffffff0f")
        testByteContent(bc, -1933587636, "cc96ffe508")
        testByteContent(bc, 923587636, "b4a8b3b803")
        testByteContent(bc, Int.MAX_VALUE, "ffffffff07")
        testByteContent(bc, Int.MIN_VALUE, "8080808008")
    }

    @Test
    fun testStreamingVarIntZigZagConversion() {
        val bc = ByteCollector()

        testZigZagByteContent(bc, 2222, "dc22")
        testZigZagByteContent(bc, -2222, "db22")
        testZigZagByteContent(bc, 1, "02")
        testZigZagByteContent(bc, 0, "00")
        testZigZagByteContent(bc, -1, "01")
        testZigZagByteContent(bc, -1933587636, "e7d281b40e")
        testZigZagByteContent(bc, 923587636, "e8d0e6f006")
        testZigZagByteContent(bc, Int.MAX_VALUE, "feffffff0f")
        testZigZagByteContent(bc, Int.MIN_VALUE, "ffffffff0f")
    }

    @Test
    fun testStreamingLittleEndianIntConversion() {
        val bc = ByteCollector()

        testLittleEndianByteContent(bc, 2222, "ae080000")
        testLittleEndianByteContent(bc, -2222, "52f7ffff")
        testLittleEndianByteContent(bc, 1, "01000000")
        testLittleEndianByteContent(bc, 0, "00000000")
        testLittleEndianByteContent(bc, -1, "ffffffff")
        testLittleEndianByteContent(bc, -1933587636, "4ccbbf8c")
        testLittleEndianByteContent(bc, 923587636, "34d40c37")
        testLittleEndianByteContent(bc, Int.MAX_VALUE, "ffffff7f")
        testLittleEndianByteContent(bc, Int.MIN_VALUE, "00000080")
    }

    private fun testZigZagByteContent(bc: ByteCollector, it: Int, hexValue: String) {
        this.testByteContent(bc, it.encodeZigZag(), hexValue)
    }

    private fun testByteContent(bc: ByteCollector, int: Int, hexValue: String) {
        bc.reserve(int.calculateVarByteLength())
        int.writeVarBytes(bc::write)

        expect(int) { initIntByVar(bc::read) }

        expect(hexValue) { bc.bytes!!.toHex() }
        bc.reset()
    }

    private fun testLittleEndianByteContent(bc: ByteCollector, int: Int, hexValue: String) {
        bc.reserve(4)
        int.writeLittleEndianBytes(bc::write)

        expect(int) { initIntLittleEndian(bc::read) }

        expect(hexValue) { bc.bytes!!.toHex() }
        bc.reset()
    }

    @Test
    fun testWrongVarInt() {
        val bytes = ByteArray(6) { -1 }
        var index = 0
        assertFailsWith<ParseException> {
            initIntByVar { bytes[index++] }
        }
    }
}
