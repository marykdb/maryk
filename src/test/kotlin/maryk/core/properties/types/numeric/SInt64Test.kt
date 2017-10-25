package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test

internal class SInt64Test {

    private val int64values = arrayOf(
            Long.MIN_VALUE,
            -6267862343234742349L,
            0,
            6267862343234742349L,
            Long.MAX_VALUE
    )

    @Test
    fun testRandom() {
        SInt64.createRandom()
    }

    @Test
    fun testStringConversion() {
        Long.MIN_VALUE.toString() shouldBe "-9223372036854775808"
        Long.MAX_VALUE.toString() shouldBe "9223372036854775807"

        int64values.forEach {
            SInt64.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        int64values.forEach {
            bc.reserve(SInt64.size)
            SInt64.writeStorageBytes(it, bc::write)
            SInt64.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        int64values.forEach {
            bc.reserve(SInt64.calculateTransportByteLength(it))
            SInt64.writeTransportBytes(it, bc::write)
            SInt64.readTransportBytes(bc::read) shouldBe it
            bc.reset()
        }
    }
}