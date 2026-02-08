package maryk.core.properties.types.numeric

import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class UInt64Test {
    private val uInt64values = arrayOf(
        UInt64.MIN_VALUE,
        UInt64.MAX_VALUE,
        6267862346434742349uL,
        0uL
    )

    @Test
    fun testRandom() {
        UInt64.createRandom()
    }

    @Test
    fun testStringConversion() {
        expect("0") { UInt64.MIN_VALUE.toString() }
        expect("18446744073709551615") { UInt64.MAX_VALUE.toString() }
        expect("9223372036854775807") { Long.MAX_VALUE.toULong().toString() }

        expect("17293822569102704640") { UInt64.ofString("17293822569102704640").toString() }

        for (uLong in uInt64values) {
            expect(uLong) { UInt64.ofString(uLong.toString()) }
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()

        val values = uInt64values.zip(
            arrayOf("0000000000000000", "ffffffffffffffff", "56fbeb98744a084d", "0000000000000000")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt64.size)
            UInt64.writeStorageBytes(value, bc::write)

            expect(hexString) { bc.bytes?.toHex() }

            expect(value) { UInt64.fromStorageByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()

        val values = uInt64values.zip(
            arrayOf("00", "ffffffffffffffffff01", "cd90a8a287f3fafd56", "00")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt64.calculateTransportByteLength(value))
            UInt64.writeTransportBytes(value, bc::write)

            expect(hexString) { bc.bytes?.toHex() }

            expect(value) { UInt64.readTransportBytes(bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testIsOfType() {
        assertTrue { UInt64.isOfType(42uL) }
        assertFalse { UInt64.isOfType(42L) }
    }

    @Test
    fun testToDoubleStaysPositiveForLargeUnsignedValues() {
        val maxAsDouble = UInt64.toDouble(ULong.MAX_VALUE)
        assertTrue { maxAsDouble > 0.0 }
        assertTrue { maxAsDouble > Long.MAX_VALUE.toDouble() }
    }
}
