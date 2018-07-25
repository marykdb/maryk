package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

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
        intsToTest.forEach {
            bc.reserve(4)
            it.writeBytes(bc::write)

            initInt(bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStreaming3Conversion() {
        val bc = ByteCollector()
        intArrayOf(
            -0x7FFFFF,
            -1,
            0,
            1,
            2222,
            0x7FFFFF
        ).forEach {
            bc.reserve(3)
            it.writeBytes(bc::write, 3)

            initInt(bc::read, 3) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testOutOfRangeConversion() {
        shouldThrow<IllegalArgumentException> {
            4.writeBytes({}, 5)
        }
    }

    @Test
    fun testZigZagAndBack() {
        intsToTest.forEach {
            it.encodeZigZag().decodeZigZag() shouldBe it
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

    private fun testZigZagByteContent(bc: ByteCollector, it: Int, hexValue: String) {
        this.testByteContent(bc, it.encodeZigZag(), hexValue)
    }

    private fun testByteContent(bc: ByteCollector, it: Int, hexValue: String) {
        bc.reserve(it.calculateVarByteLength())
        it.writeVarBytes(bc::write)

        initIntByVar(bc::read) shouldBe it

        bc.bytes!!.toHex() shouldBe hexValue
        bc.reset()
    }

    @Test
    fun testWrongVarInt() {
        val bytes = ByteArray(6, { -1 })
        var index = 0
        shouldThrow<ParseException> {
            initIntByVar { bytes[index++] }
        }
    }
}
