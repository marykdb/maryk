package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.extensions.bytes.toBytes
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
    fun test8StringConversion() {
        Byte.MIN_VALUE.toString() shouldBe "-128"
        Byte.MAX_VALUE.toString() shouldBe "127"

        int8values.forEach {
            Int8.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testInt8BytesConversion() {
        val bytes = ByteArray(33)

        int8values.forEach {
            Int8.ofBytes(it.toBytes()) shouldBe it
            Int8.ofBytes(it.toBytes(bytes, 10), 10) shouldBe it
            Int8.ofBytes(Int8.toBytes(it, bytes, 10), 10) shouldBe it
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