package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test

internal class SInt32Test {

    private val int32values = intArrayOf(
            Int.MIN_VALUE,
            -424234234,
            0,
            424234234,
            Int.MAX_VALUE
    )

    @Test
    fun testRandom() {
        SInt32.createRandom()
    }

    @Test
    fun testStringConversion() {
        Int.MIN_VALUE.toString() shouldBe "-2147483648"
        Int.MAX_VALUE.toString() shouldBe "2147483647"

        int32values.forEach {
            SInt32.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        int32values.forEach {
            SInt32.writeStorageBytes(it, bc::reserve, bc::write)
            SInt32.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        int32values.forEach {
            bc.reserve(SInt32.calculateTransportByteSize(it))
            SInt32.writeTransportBytes(it, bc::write)
            SInt32.readTransportBytes(bc::read) shouldBe it
            bc.reset()
        }
    }
}