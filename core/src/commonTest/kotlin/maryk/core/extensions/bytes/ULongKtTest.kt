package maryk.core.extensions.bytes

import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

@OptIn(ExperimentalUnsignedTypes::class)
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
        longsToTest.forEach { uLong ->
            bc.reserve(8)
            uLong.writeBytes(bc::write, 8)

            expect(uLong) { initULong(bc::read, 8) }
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
        ).forEach { uLong ->
            bc.reserve(7)
            uLong.writeBytes(bc::write, 7)

            expect(uLong) { initULong(bc::read, 7) }
            bc.reset()
        }
    }

    @Test
    fun testByteArrayConversion() {
        longsToTest.forEach { uLong ->
            expect(uLong) { uLong.toByteArray().toULong() }
        }
    }

    @Test
    fun testOutOfRangeConversion() {
        assertFailsWith<IllegalArgumentException> {
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

    private fun testByteContent(bc: ByteCollector, uLong: ULong, hexValue: String) {
        bc.reserve(uLong.calculateVarByteLength())
        uLong.writeVarBytes(bc::write)

        expect(uLong) { initULongByVar(bc::read) }

        expect(hexValue) { bc.bytes!!.toHex() }
        bc.reset()
    }
}
