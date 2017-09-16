package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test
import kotlin.test.assertEquals

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
            assertEquals(it, Float32.ofString(it.toString()))
        }
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        float32values.forEach {
            Float32.writeBytes(it, bc::reserve, bc::write)
            Float32.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }
}