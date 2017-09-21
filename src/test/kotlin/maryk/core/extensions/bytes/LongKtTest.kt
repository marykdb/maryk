package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import org.junit.Test

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
        longsToTest.forEach {
            bc.reserve(8)
            it.writeBytes(bc::write, 8)

            initLong(bc::read, 8) shouldBe it
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
        ).forEach {
            bc.reserve(7)
            it.writeBytes(bc::write, 7)

            initLong(bc::read, 7) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testOutOfRangeConversion() {
        shouldThrow<IllegalArgumentException> {
            4L.writeBytes({}, 9)
        }
    }

    @Test
    fun testZigZagAndBack() {
        longsToTest.forEach {
            it.encodeZigZag().decodeZigZag() shouldBe it
        }
    }

    @Test
    fun testStreamingVarIntConversion() {
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
    fun testStreamingVarIntZigZagConversion() {
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

    private fun testZigZagByteContent(bc: ByteCollector, it: Long, hexValue: String) {
        testByteContent(bc, it.encodeZigZag(), hexValue)
    }

    private fun testByteContent(bc: ByteCollector, it: Long, hexValue: String) {
        bc.reserve(it.computeVarByteSize())
        it.writeVarBytes(bc::write)

        initLongByVar(bc::read) shouldBe it

        bc.bytes!!.toHex() shouldBe hexValue
        bc.reset()
    }

    @Test
    fun testWrongVarInt() {
        val bytes = ByteArray(11, { -1 })
        var index = 0
        shouldThrow<ParseException> {
            initLongByVar { bytes[index++] }
        }
    }
}