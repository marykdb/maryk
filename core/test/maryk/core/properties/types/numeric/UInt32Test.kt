package maryk.core.properties.types.numeric

import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.expect

internal class UInt32Test {

    private val uInt32values = arrayOf(UInt32.MIN_VALUE, UInt32.MAX_VALUE, 4242342349u)

    @Test
    fun testRandom() {
        UInt32.createRandom()
    }

    @Test
    fun testStringConversion() {
        expect("0") { UInt32.MIN_VALUE.toString() }
        expect("4294967295") { UInt32.MAX_VALUE.toString() }

        for (uInt in uInt32values) {
            expect(uInt) { UInt32.ofString(uInt.toString()) }
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()

        val values = uInt32values.zip(
            arrayOf("00000000", "ffffffff", "fcdd01cd")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt32.size)
            UInt32.writeStorageBytes(value, bc::write)

            expect(hexString) { bc.bytes?.toHexString() }

            expect(value) { UInt32.fromStorageByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()

        val values = uInt32values.zip(
            arrayOf("00", "ffffffff0f", "cd83f4e60f")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt32.calculateTransportByteLength(value))
            UInt32.writeTransportBytes(value, bc::write)

            expect(hexString) { bc.bytes?.toHexString() }

            expect(value) { UInt32.readTransportBytes(bc::read) }
            bc.reset()
        }
    }
}
