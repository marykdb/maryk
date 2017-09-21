package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollector
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

    private fun testByteContent(bc: ByteCollector, it: Long, hexValue: String) {
        bc.reserve(it.computeVarByteSize())
        it.writeVarBytes(bc::write)

        initLongByVar(bc::read) shouldBe it

        bc.bytes!!.toHex() shouldBe hexValue
        bc.reset()
    }
}