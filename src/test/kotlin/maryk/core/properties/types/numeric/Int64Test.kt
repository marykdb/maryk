package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test

internal class Int64Test {

    private val int64values = arrayOf(
            Long.MIN_VALUE,
            -6267862343234742349L,
            0,
            6267862343234742349L,
            Long.MAX_VALUE
    )

    @Test
    fun testRandom() {
        Int64.createRandom()
    }

    @Test
    fun testStringConversion() {
        Long.MIN_VALUE.toString() shouldBe "-9223372036854775808"
        Long.MAX_VALUE.toString() shouldBe "9223372036854775807"

        int64values.forEach {
            Int64.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        int64values.forEach {
            Int64.writeBytes(it, bc::reserve, bc::write)
            Int64.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }
}