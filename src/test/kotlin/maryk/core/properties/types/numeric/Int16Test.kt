package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.extensions.bytes.toBytes
import org.junit.Test

internal class Int16Test {
    private val int16values = shortArrayOf(
            Short.MIN_VALUE,
            -839,
            0,
            12312,
            Short.MAX_VALUE
    )

    @Test
    fun testRandom() {
        Int16.createRandom()
    }

    @Test
    fun test16StringConversion() {
        Short.MIN_VALUE.toString() shouldBe "-32768"
        Short.MAX_VALUE.toString() shouldBe "32767"

        int16values.forEach {
            Int16.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testInt16BytesConversion() {
        val bytes = ByteArray(33)

        int16values.forEach {
            Int16.ofBytes(it.toBytes()) shouldBe it
            Int16.ofBytes(it.toBytes(bytes, 10), 10) shouldBe it
            Int16.ofBytes(Int16.toBytes(it, bytes, 10), 10) shouldBe it
        }
    }
}