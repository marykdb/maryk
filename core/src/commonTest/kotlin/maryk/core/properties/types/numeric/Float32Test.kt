package maryk.core.properties.types.numeric

import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

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
        for (it in float32values) {
            Float32.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        for (it in float32values) {
            bc.reserve(Float32.size)
            Float32.writeStorageBytes(it, bc::write)
            Float32.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        for (it in float32values) {
            bc.reserve(Float32.calculateTransportByteLength(it))
            Float32.writeTransportBytes(it, bc::write)
            Float32.readTransportBytes(bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testOfNativeTypes() {
        Float32.ofLong(2131232) shouldBe 2131232F
        Float32.ofDouble(12213.121) shouldBe 12213.121F
        Float32.ofInt(1221321) shouldBe 1221321F
    }

    @Test
    fun testIsOfType() {
        Float32.isOfType(22.0F) shouldBe true
        Float32.isOfType(24L) shouldBe false
    }
}
