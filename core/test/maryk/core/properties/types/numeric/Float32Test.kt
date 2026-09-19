package maryk.core.properties.types.numeric

import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class Float32Test {

    private val float32values = floatArrayOf(
        Float.NEGATIVE_INFINITY,
        Float.MIN_VALUE,
        -626786234.3234742349F,
        0.0F,
        6267862343.234742349F,
        Float.MAX_VALUE,
        Float.POSITIVE_INFINITY,
        Float.NaN
    )

    @Test
    fun testRandom() {
        Float32.createRandom()
    }

    @Test
    fun testStringConversion() {
        for (float in float32values) {
            expect(float) { Float32.ofString(float.toString()) }
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        for (float in float32values) {
            bc.reserve(Float32.size)
            Float32.writeStorageBytes(float, bc::write)
            expect(float) { Float32.fromStorageByteReader(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        for (float in float32values) {
            bc.reserve(Float32.calculateTransportByteLength(float))
            Float32.writeTransportBytes(float, bc::write)
            expect(float) { Float32.readTransportBytes(bc::read) }
            bc.reset()
        }
    }

    @Test
    fun testOfNativeTypes() {
        expect(2131232F) { Float32.ofLong(2131232) }
        expect(12213.121F) { Float32.ofDouble(12213.121) }
        expect(1221321F) { Float32.ofInt(1221321) }
    }

    @Test
    fun testIsOfType() {
        assertTrue { Float32.isOfType(22.0F) }
        assertFalse { Float32.isOfType(24L) }
    }
}
