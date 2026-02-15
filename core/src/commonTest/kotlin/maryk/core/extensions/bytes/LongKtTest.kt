package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class LongKtTest {
    private val longsToTest = longArrayOf(
        Long.MIN_VALUE,
        -4786131286145765123,
        -2222,
        -1,
        0,
        1,
        2222,
        4786131286145765123,
        Long.MAX_VALUE
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        longsToTest.forEach { long ->
            bc.reserve(8)
            long.writeBytes(bc::write, 8)

            expect(long) { initLong(bc::read, 8) }
            bc.reset()
        }
    }

    @Test
    fun testStreaming7Conversion() {
        val bc = ByteCollector()
        longArrayOf(
            MIN_SEVEN_VALUE,
            -999999999L,
            -1L,
            0L,
            1L,
            1504201744L,
            999999999,
            MAX_SEVEN_VALUE
        ).forEach { long ->
            bc.reserve(7)
            long.writeBytes(bc::write, 7)

            expect(long) { initLong(bc::read, 7) }
            bc.reset()
        }
    }

    @Test
    fun testOutOfRangeConversion() {
        assertFailsWith<IllegalArgumentException> {
            4L.writeBytes({}, 9)
        }
    }

    @Test
    fun testZigZagAndBack() {
        longsToTest.forEach { long ->
            expect(long) { long.encodeZigZag().decodeZigZag() }
        }
    }

    @Test
    fun testStreamingVarLongConversion() {
        val bc = ByteCollector()

        testByteContent(bc, 2222, "ae11")
        testByteContent(bc, -2222, "d2eeffffffffffffff01")
        testByteContent(bc, 1, "01")
        testByteContent(bc, 0, "00")
        testByteContent(bc, -1, "ffffffffffffffffff01")
        testByteContent(bc, -4786131286145765123, "fde197c7c0de8fcabd01")
        testByteContent(bc, 4786131286145765123, "839ee8b8bfa1f0b542")
        testByteContent(bc, Long.MAX_VALUE, "ffffffffffffffff7f")
        testByteContent(bc, Long.MIN_VALUE, "80808080808080808001")
    }

    @Test
    fun testStreamingVarLongZigZagConversion() {
        val bc = ByteCollector()

        testZigZagByteContent(bc, 2222, "dc22")
        testZigZagByteContent(bc, -2222, "db22")
        testZigZagByteContent(bc, 1, "02")
        testZigZagByteContent(bc, 0, "00")
        testZigZagByteContent(bc, -1, "01")
        testZigZagByteContent(bc, -4786131286145765123, "85bcd0f1fec2e0eb8401")
        testZigZagByteContent(bc, 4786131286145765123, "86bcd0f1fec2e0eb8401")
        testZigZagByteContent(bc, Long.MAX_VALUE, "feffffffffffffffff01")
        testZigZagByteContent(bc, Long.MIN_VALUE, "ffffffffffffffffff01")
    }

    @Test
    fun testStreamingLongLittleEndianConversion() {
        val bc = ByteCollector()

        testLittleEndianByteContent(bc, 2222, "ae08000000000000")
        testLittleEndianByteContent(bc, -2222, "52f7ffffffffffff")
        testLittleEndianByteContent(bc, 1, "0100000000000000")
        testLittleEndianByteContent(bc, 0, "0000000000000000")
        testLittleEndianByteContent(bc, -1, "ffffffffffffffff")
        testLittleEndianByteContent(bc, -4786131286145765123, "fdf0e508f43e94bd")
        testLittleEndianByteContent(bc, 4786131286145765123, "030f1af70bc16b42")
        testLittleEndianByteContent(bc, Long.MAX_VALUE, "ffffffffffffff7f")
        testLittleEndianByteContent(bc, Long.MIN_VALUE, "0000000000000080")
    }

    private fun testZigZagByteContent(bc: ByteCollector, it: Long, hexValue: String) {
        testByteContent(bc, it.encodeZigZag(), hexValue)
    }

    private fun testByteContent(bc: ByteCollector, long: Long, hexValue: String) {
        bc.reserve(long.calculateVarByteLength())
        long.writeVarBytes(bc::write)

        expect(long) { initLongByVar(bc::read) }

        expect(hexValue) { bc.bytes!!.toHexString() }
        bc.reset()
    }

    private fun testLittleEndianByteContent(bc: ByteCollector, long: Long, hexValue: String) {
        bc.reserve(8)
        long.writeLittleEndianBytes(bc::write)

        expect(long) { initLongLittleEndian(bc::read) }

        expect(hexValue) { bc.bytes!!.toHexString() }
        bc.reset()
    }

    @Test
    fun testWrongVarInt() {
        val bytes = ByteArray(11) { -1 }
        var index = 0
        assertFailsWith<ParseException> {
            initLongByVar { bytes[index++] }
        }
    }

    @Test
    fun testWrongVarIntOverflowPayload() {
        val bytes = byteArrayOf(-128, -128, -128, -128, -128, -128, -128, -128, -128, 2)
        var index = 0
        assertFailsWith<ParseException> {
            initLongByVar { bytes[index++] }
        }
    }
}
