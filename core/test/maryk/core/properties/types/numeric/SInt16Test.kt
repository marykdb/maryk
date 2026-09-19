package maryk.core.properties.types.numeric

import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class SInt16Test {
    private val int16values = shortArrayOf(
        Short.MIN_VALUE,
        -839,
        0,
        12312,
        Short.MAX_VALUE
    )

    @Test
    fun testRandom() {
        SInt16.createRandom()
    }

    @Test
    fun testRandomContainsNegativeAndPositiveValues() {
        val values = List(512) { SInt16.createRandom() }
        assertTrue { values.any { it < 0 } }
        assertTrue { values.any { it > 0 } }
    }

    @Test
    fun testStringConversion() {
        expect("-32768") { Short.MIN_VALUE.toString() }
        expect("32767") { Short.MAX_VALUE.toString() }

        for (short in int16values) {
            expect(short) { SInt16.ofString(short.toString()) }
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        for (short in int16values) {
            bc.reserve(SInt16.size)
            SInt16.writeStorageBytes(short, bc::write)
            expect(short) { SInt16.fromStorageByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        for (short in int16values) {
            bc.reserve(SInt16.calculateTransportByteLength(short))
            SInt16.writeTransportBytes(short, bc::write)
            expect(short) { SInt16.readTransportBytes(bc::read) }
            bc.reset()
        }
    }


    @Test
    fun testOfNativeTypes() {
        expect(12345.toShort()) { SInt16.ofLong(12345) }
        expect(12.toShort()) { SInt16.ofDouble(12.0) }
        expect(12.toShort()) { SInt16.ofInt(12) }
    }

    @Test
    fun testIsOfType() {
        assertTrue { SInt16.isOfType(12.toShort()) }
        assertFalse { SInt16.isOfType(1234L) }
    }
}
