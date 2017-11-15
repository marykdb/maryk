package maryk.core.properties.types.numeric

import maryk.core.properties.ByteCollector
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
        Float.MIN_VALUE.toString() shouldBe "1.4E-45"
        Float.MAX_VALUE.toString() shouldBe "3.4028235E38"

        float32values.forEach {
            Float32.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        float32values.forEach {
            bc.reserve(Float32.size)
            Float32.writeStorageBytes(it, bc::write)
            Float32.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        float32values.forEach {
            bc.reserve(Float32.calculateTransportByteLength(it))
            Float32.writeTransportBytes(it, bc::write)
            Float32.readTransportBytes(bc::read) shouldBe it
            bc.reset()
        }
    }
}