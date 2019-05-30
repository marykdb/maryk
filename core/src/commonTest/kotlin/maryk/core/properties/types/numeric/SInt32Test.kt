package maryk.core.properties.types.numeric

import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class SInt32Test {
    private val int32values = intArrayOf(
        Int.MIN_VALUE,
        -424234234,
        0,
        424234234,
        Int.MAX_VALUE
    )

    @Test
    fun testRandom() {
        SInt32.createRandom()
    }

    @Test
    fun testStringConversion() {
        expect("-2147483648") { Int.MIN_VALUE.toString() }
        expect("2147483647") { Int.MAX_VALUE.toString() }

        for (int in int32values) {
            expect(int) { SInt32.ofString(int.toString()) }
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        for (int in int32values) {
            bc.reserve(SInt32.size)
            SInt32.writeStorageBytes(int, bc::write)
            expect(int) { SInt32.fromStorageByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        for (int in int32values) {
            bc.reserve(SInt32.calculateTransportByteLength(int))
            SInt32.writeTransportBytes(int, bc::write)
            expect(int) { SInt32.readTransportBytes(bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testOfNativeTypes() {
        expect(123) { SInt32.ofLong(123) }
        expect(12) { SInt32.ofDouble(12.0) }
        expect(12) { SInt32.ofInt(12) }
    }

    @Test
    fun testIsOfType() {
        assertTrue { SInt32.isOfType(12) }
        assertFalse { SInt32.isOfType(1234L) }
    }
}
