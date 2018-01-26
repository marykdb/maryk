package maryk.core.properties.types.numeric

import maryk.core.properties.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class Float64Test {
    private val float64values = doubleArrayOf(
        Double.NEGATIVE_INFINITY,
        Double.MIN_VALUE,
        -626786234.3234742349,
        0.0,
        6267862343.234742349,
        Double.MAX_VALUE,
        Double.POSITIVE_INFINITY,
        Double.NaN
    )

    @Test
    fun testRandom() {
        Float64.createRandom()
    }

    @Test
    fun testStringConversion() {
        for (it in float64values) {
            Float64.ofString(it.toString()) shouldBe  it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        for (it in float64values) {
            bc.reserve(Float64.size)
            Float64.writeStorageBytes(it, bc::write)
            Float64.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        for (it in float64values) {
            bc.reserve(Float64.calculateTransportByteLength(it))
            Float64.writeTransportBytes(it, bc::write)
            val value = Float64.readTransportBytes(bc::read)
            value shouldBe it
            bc.reset()
        }
    }
}