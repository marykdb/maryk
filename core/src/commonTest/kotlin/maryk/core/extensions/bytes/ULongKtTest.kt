@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.core.extensions.bytes

import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class ULongKtTest {
    private val longsToTest = ulongArrayOf(
        0uL,
        1uL,
        2222uL,
        4786131286145765123uL,
        ULong.MAX_VALUE
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        longsToTest.forEach {
            bc.reserve(8)
            it.writeBytes(bc::write, 8)

            initULong(bc::read, 8) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStreaming7Conversion() {
        val bc = ByteCollector()
        ulongArrayOf(
            0uL,
            1uL,
            1504201744uL,
            999999999uL,
            MAX_SEVEN_VALUE.toULong()
        ).forEach {
            bc.reserve(7)
            it.writeBytes(bc::write, 7)

            initULong(bc::read, 7) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testOutOfRangeConversion() {
        shouldThrow<IllegalArgumentException> {
            4uL.writeBytes({}, 9)
        }
    }

    @Test
    fun testStreamingVarULongConversion() {
        val bc = ByteCollector()

        testByteContent(bc, 2222uL, "ae11")
        testByteContent(bc, 0uL, "00")
        testByteContent(bc, 4786131286145765123uL, "839ee8b8bfa1f0b542")
        testByteContent(bc, ULong.MAX_VALUE, "ffffffffffffffffff01")
        testByteContent(bc, ULong.MIN_VALUE, "00")
    }

    private fun testByteContent(bc: ByteCollector, it: ULong, hexValue: String) {
        bc.reserve(it.calculateVarByteLength())
        it.writeVarBytes(bc::write)

        initULongByVar(bc::read) shouldBe it

        bc.bytes!!.toHex() shouldBe hexValue
        bc.reset()
    }
}
