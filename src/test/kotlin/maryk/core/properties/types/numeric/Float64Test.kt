package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.extensions.bytes.toBytes
import org.junit.Test
import kotlin.test.assertEquals

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
    fun test64StringConversion() {
        Double.MIN_VALUE.toString() shouldBe "4.9E-324"
        Double.MAX_VALUE.toString() shouldBe "1.7976931348623157E308"

        float64values.forEach {
            assertEquals(it,  Float64.ofString(it.toString()))
        }
    }

    @Test
    fun testFloat64BytesConversion() {
        val bytes = ByteArray(33)

        float64values.forEach {
            assertEquals(it,  Float64.ofBytes(it.toBytes()))
            assertEquals(it,  Float64.ofBytes(it.toBytes(bytes, 10), 10))
            assertEquals(it,  Float64.ofBytes(Float64.toBytes(it, bytes, 10), 10))
        }
    }
}