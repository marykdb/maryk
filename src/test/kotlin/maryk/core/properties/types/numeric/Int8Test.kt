package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test

internal class Int8Test {
    private val int8values = byteArrayOf(
            Byte.MIN_VALUE,
            -100,
            0,
            89,
            127,
            Byte.MAX_VALUE
    )

    @Test
    fun testRandom() {
        Int8.createRandom()
    }

    @Test
    fun testStringConversion() {
        Byte.MIN_VALUE.toString() shouldBe "-128"
        Byte.MAX_VALUE.toString() shouldBe "127"

        int8values.forEach {
            Int8.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        int8values.forEach {
            Int8.writeBytes(it, bc::reserve, bc::write)
            Int8.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }
}