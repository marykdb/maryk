package maryk.core.properties.types.numeric

import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.expect

internal class UInt16Test {
    private val uInt16values = arrayOf(UInt16.MIN_VALUE, UInt16.MAX_VALUE, 839.toUShort(), 12312.toUShort())

    @Test
    fun testRandom() {
        UInt16.createRandom()
    }

    @Test
    fun testStringConversion() {
        expect("0") { UInt16.MIN_VALUE.toString() }
        expect("65535") { UInt16.MAX_VALUE.toString() }

        for (uShort in uInt16values) {
            expect(uShort) { UInt16.ofString(uShort.toString()) }
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()

        val values = uInt16values.zip(
            arrayOf("0000", "ffff", "0347", "3018")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt16.size)
            UInt16.writeStorageBytes(value, bc::write)

            expect(hexString) { bc.bytes?.toHex() }

            expect(value) { UInt16.fromStorageByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()

        val values = uInt16values.zip(
            arrayOf("00", "ffff03", "c706", "9860")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt16.calculateTransportByteLength(value))
            UInt16.writeTransportBytes(value, bc::write)

            expect(hexString) { bc.bytes?.toHex() }

            expect(value) { UInt16.readTransportBytes(bc::read) }
            bc.reset()
        }
    }
}
