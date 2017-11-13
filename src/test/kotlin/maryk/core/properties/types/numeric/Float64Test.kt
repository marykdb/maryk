package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import kotlin.test.assertEquals
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
        Double.MIN_VALUE.toString() shouldBe "4.9E-324"
        Double.MAX_VALUE.toString() shouldBe "1.7976931348623157E308"

        float64values.forEach {
            assertEquals(it,  Float64.ofString(it.toString()))
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        float64values.forEach {
            bc.reserve(Float64.size)
            Float64.writeStorageBytes(it, bc::write)
            assertEquals(it, Float64.fromStorageByteReader(bc.size, bc::read))
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        float64values.forEach {
            bc.reserve(Float64.calculateTransportByteLength(it))
            Float64.writeTransportBytes(it, bc::write)
            val value = Float64.readTransportBytes(bc::read)
            assertEquals(value, it)
            bc.reset()
        }
    }
}