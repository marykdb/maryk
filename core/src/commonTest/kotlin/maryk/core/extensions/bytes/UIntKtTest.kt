package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

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

            expect(hex) { bc.bytes?.toHexString() }

            expect(value) { initUInt(bc::read) }
            bc.reset()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun testStreaming3Conversion() {
        val bc = ByteCollector()
        uintArrayOf(
            0u,
            1u,
            2222u,
            0x7FFFFFu
        ).forEach { uInt ->
            bc.reserve(3)
            uInt.writeBytes(bc::write, 3)

            expect(uInt) { initUInt(bc::read, 3) }
            bc.reset()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun testStreaming2Conversion() {
        val bc = ByteCollector()
        uintArrayOf(
            0u,
            1u,
            2222u,
            0xFFFFu
        ).forEach { uInt ->
            bc.reserve(2)
            uInt.writeBytes(bc::write, 2)

            expect(uInt) { initUInt(bc::read, 2) }
            bc.reset()
        }
    }

    @Test
    fun testOutOfRangeConversion() {
        assertFailsWith<IllegalArgumentException> {
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

    private fun testByteContent(bc: ByteCollector, uInt: UInt, hexValue: String) {
        bc.reserve(uInt.calculateVarByteLength())
        uInt.writeVarBytes(bc::write)

        expect(uInt) { initUIntByVar(bc::read) }

        expect(hexValue) { bc.bytes!!.toHexString() }
        bc.reset()
    }

    @Test
    fun testWrongVarUInt() {
        val bytes = ByteArray(6) { -1 }
        var index = 0
        assertFailsWith<ParseException> {
            initUIntByVar { bytes[index++] }
        }
    }

    @Test
    fun testWrongVarUIntOverflowPayload() {
        val bytes = byteArrayOf(-1, -1, -1, -1, 16)
        var index = 0
        assertFailsWith<ParseException> {
            initUIntByVar { bytes[index++] }
        }
    }

    @Test
    fun testWrongVarUIntWithExtraInfoTooLong() {
        val bytes = byteArrayOf(-128, -128, -128, -128, -128, 1)
        var index = 0
        assertFailsWith<ParseException> {
            initUIntByVarWithExtraInfo({ bytes[index++] }) { _, _ -> }
        }
    }
}
