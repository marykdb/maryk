package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
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
    fun testStringConversion() {
        Short.MIN_VALUE.toString() shouldBe "-32768"
        Short.MAX_VALUE.toString() shouldBe "32767"

        int16values.forEach {
            Int16.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        int16values.forEach {
            Int16.writeBytes(it, bc::reserve, bc::write)
            Int16.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }
}