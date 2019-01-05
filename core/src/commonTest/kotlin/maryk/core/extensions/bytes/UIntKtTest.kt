@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class UIntKtTest {
    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        arrayOf(
            UInt.MIN_VALUE to "00000000",
            0u to "00000000",
            1u to "00000001",
            2222u to "000008ae",
            923587636u to "370cd434",
            UInt.MAX_VALUE to "ffffffff"
        ).forEach { (value, hex) ->
            bc.reserve(4)
            value.writeBytes(bc::write)

            bc.bytes?.toHex() shouldBe hex

            initUInt(bc::read) shouldBe value
            bc.reset()
        }
    }

    @Test
    fun testStreaming3Conversion() {
        val bc = ByteCollector()
        uintArrayOf(
            0u,
            1u,
            2222u,
            0x7FFFFFu
        ).forEach {
            bc.reserve(3)
            it.writeBytes(bc::write, 3)

            initUInt(bc::read, 3) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testOutOfRangeConversion() {
        shouldThrow<IllegalArgumentException> {
            4u.writeBytes({}, 5)
        }
    }

    @Test
    fun testStreamingVarIntConversion() {
        val bc = ByteCollector()

        testByteContent(bc, 2222u, "ae11")
        testByteContent(bc, 1u, "01")
        testByteContent(bc, 0u, "00")
        testByteContent(bc, 923587636u, "b4a8b3b803")
        testByteContent(bc, UInt.MAX_VALUE, "ffffffff0f")
        testByteContent(bc, UInt.MIN_VALUE, "00")
    }

    private fun testByteContent(bc: ByteCollector, it: UInt, hexValue: String) {
        bc.reserve(it.calculateVarByteLength())
        it.writeVarBytes(bc::write)

        initUIntByVar(bc::read) shouldBe it

        bc.bytes!!.toHex() shouldBe hexValue
        bc.reset()
    }

    @Test
    fun testWrongVarUInt() {
        val bytes = ByteArray(6) { -1 }
        var index = 0
        shouldThrow<ParseException> {
            initUIntByVar { bytes[index++] }
        }
    }
}
