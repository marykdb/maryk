package maryk.core.properties.types.numeric

import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class SInt8Test {
    private val int8values = byteArrayOf(
        Byte.MIN_VALUE,
        -100,
        0,
        89,
        127,
        Byte.MAX_VALUE
    )

    @Test
    fun testRandom() {
        SInt8.createRandom()
    }

    @Test
    fun testRandomContainsNegativeAndPositiveValues() {
        val values = List(256) { SInt8.createRandom() }
        assertTrue { values.any { it < 0 } }
        assertTrue { values.any { it > 0 } }
    }

    @Test
    fun testStringConversion() {
        expect("-128") { Byte.MIN_VALUE.toString() }
        expect("127") { Byte.MAX_VALUE.toString() }

        for (byte in int8values) {
            expect(byte) { SInt8.ofString(byte.toString()) }
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        for (byte in int8values) {
            bc.reserve(SInt8.size)
            SInt8.writeStorageBytes(byte, bc::write)
            expect(byte) { SInt8.fromStorageByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        for (byte in int8values) {
            bc.reserve(SInt8.calculateTransportByteLength(byte))
            SInt8.writeTransportBytes(byte, bc::write)
            expect(byte) { SInt8.readTransportBytes(bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testOfNativeTypes() {
        expect(123.toByte()) { SInt8.ofLong(123) }
        expect(12.toByte()) { SInt8.ofDouble(12.0) }
        expect(12.toByte()) { SInt8.ofInt(12) }
    }

    @Test
    fun testIsOfType() {
        assertTrue { SInt8.isOfType(12.toByte()) }
        assertFalse { SInt8.isOfType(1234L) }
    }
}
