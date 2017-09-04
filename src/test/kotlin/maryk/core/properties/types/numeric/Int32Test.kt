package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.extensions.bytes.toBytes
import org.junit.Test

internal class Int32Test {

    private val int32values = intArrayOf(
            Int.MIN_VALUE,
            -424234234,
            0,
            424234234,
            Int.MAX_VALUE
    )

    @Test
    fun testRandom() {
        Int32.createRandom()
    }

    @Test
    fun test32StringConversion() {
        Int.MIN_VALUE.toString() shouldBe "-2147483648"
        Int.MAX_VALUE.toString() shouldBe "2147483647"

        int32values.forEach {
            Int32.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testInt32BytesConversion() {
        val bytes = ByteArray(33)

        int32values.forEach {
            Int32.ofBytes(it.toBytes()) shouldBe it
            Int32.ofBytes(it.toBytes(bytes, 10), 10) shouldBe it
            Int32.ofBytes(Int32.toBytes(it, bytes, 10), 10) shouldBe it
        }
    }
}