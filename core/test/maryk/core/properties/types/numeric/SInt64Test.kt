package maryk.core.properties.types.numeric

import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class SInt64Test {
    private val int64values = arrayOf(
        Long.MIN_VALUE,
        -6267862343234742349L,
        0,
        6267862343234742349L,
        Long.MAX_VALUE
    )

    @Test
    fun testRandom() {
        SInt64.createRandom()
    }

    @Test
    fun testStringConversion() {
        expect("-9223372036854775808") { Long.MIN_VALUE.toString() }
        expect("9223372036854775807") { Long.MAX_VALUE.toString() }

        for (long in int64values) {
            expect(long) { SInt64.ofString(long.toString()) }
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        for (long in int64values) {
            bc.reserve(SInt64.size)
            SInt64.writeStorageBytes(long, bc::write)
            expect(long) { SInt64.fromStorageByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        int64values.forEach { long ->
            bc.reserve(SInt64.calculateTransportByteLength(long))
            SInt64.writeTransportBytes(long, bc::write)
            expect(long) { SInt64.readTransportBytes(bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testOfNativeTypes() {
        expect(123L) { SInt64.ofLong(123) }
        expect(12L) { SInt64.ofDouble(12.0) }
        expect(12L) { SInt64.ofInt(12) }
    }

    @Test
    fun testIsOfType() {
        assertTrue { SInt64.isOfType(12L) }
        assertFalse { SInt64.isOfType(1234) }
    }
}
