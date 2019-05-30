package maryk.core.properties.types.numeric

import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.expect

internal class UInt8Test {
    private val uInt8values = arrayOf(UInt8.MIN_VALUE, UInt8.MAX_VALUE, 89.toUByte(), 127.toUByte())

    @Test
    fun testRandom() {
        UInt8.createRandom()
    }

    @Test
    fun testStringConversion() {
        expect("0") { UInt8.MIN_VALUE.toString() }
        expect("255") { UInt8.MAX_VALUE.toString() }

        for (uByte in uInt8values) {
            expect(uByte) { UInt8.ofString(uByte.toString()) }
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()

        val values = uInt8values.zip(
            arrayOf("00", "ff", "59", "7f")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt8.size)
            UInt8.writeStorageBytes(value, bc::write)

            expect(hexString) { bc.bytes?.toHex() }

            expect(value) { UInt8.fromStorageByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()

        val values = uInt8values.zip(
            arrayOf("00", "ff01", "59", "7f")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt8.calculateTransportByteLength(value))
            UInt8.writeTransportBytes(value, bc::write)

            expect(hexString) { bc.bytes?.toHex() }

            expect(value) { UInt8.readTransportBytes(bc::read) }
            bc.reset()
        }
    }
}
