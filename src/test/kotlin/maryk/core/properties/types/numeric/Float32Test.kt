package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.extensions.bytes.toBytes
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
    fun test64StringConversion() {
        Float.MIN_VALUE.toString() shouldBe "1.4E-45"
        Float.MAX_VALUE.toString() shouldBe "3.4028235E38"

        float32values.forEach {
            assertEquals(it, Float32.ofString(it.toString()))
        }
    }

    @Test
    fun testFloat32BytesConversion() {
        val bytes = ByteArray(33)

        float32values.forEach {
            assertEquals(it, Float32.ofBytes(it.toBytes()))
            assertEquals(it, Float32.ofBytes(it.toBytes(bytes, 10), 10))
            assertEquals(it, Float32.ofBytes(Float32.toBytes(it, bytes, 10), 10))
        }
    }
}