package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test

internal class UInt16Test {
    private val uInt16values = arrayOf(UInt16.MIN_VALUE, UInt16.MAX_VALUE, 839.toUInt16(), 12312.toShort().toUInt16())

    @Test
    fun testRandom() {
        UInt16.createRandom()
    }

    @Test
    fun testHashCode() {
        UInt16.MAX_VALUE.hashCode() shouldBe Short.MAX_VALUE.hashCode()
    }

    @Test
    fun testCompare() {
        UInt16.MAX_VALUE.compareTo(UInt16.MIN_VALUE) shouldBe 1
        344.toUInt16().compareTo(344.toUInt16()) shouldBe 0
        144.toUInt16().compareTo(344.toUInt16()) shouldBe -1
    }

    @Test
    fun test16StringConversion() {
        UInt16.MIN_VALUE.toString() shouldBe "0"
        UInt16.MAX_VALUE.toString() shouldBe "65535"

        uInt16values.forEach {
            UInt16.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testInt16BytesConversion() {
        val bytes = ByteArray(33)

        uInt16values.forEach {
            UInt16.ofBytes(it.toBytes()) shouldBe it
            UInt16.ofBytes(it.toBytes(bytes, 10), 10) shouldBe it
            UInt16.ofBytes(UInt16.toBytes(it, bytes, 10), 10) shouldBe it
        }
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        uInt16values.forEach {
            UInt16.writeBytes(it, bc::reserve, bc::write)
            UInt16.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }
}