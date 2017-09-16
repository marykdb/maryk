package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.extensions.bytes.toBytes
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
    fun test64StringConversion() {
        Long.MIN_VALUE.toString() shouldBe "-9223372036854775808"
        Long.MAX_VALUE.toString() shouldBe "9223372036854775807"

        int64values.forEach {
            Int64.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testInt64BytesConversion() {
        val bytes = ByteArray(33)

        int64values.forEach {
            Int64.ofBytes(it.toBytes()) shouldBe it
            Int64.ofBytes(it.toBytes(bytes, 10), 10) shouldBe it
            Int64.ofBytes(Int64.toBytes(it, bytes, 10), 10) shouldBe it
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