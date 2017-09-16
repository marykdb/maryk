package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import maryk.core.properties.types.UInt64
import maryk.core.properties.types.toUInt64
import org.junit.Test

internal class UInt64Test {

    private val uInt64values = arrayOf(UInt64.MIN_VALUE, UInt64.MAX_VALUE, 6267862346434742349L.toUInt64())

    @Test
    fun testRandom() {
        UInt64.createRandom()
    }

    @Test
    fun testHashCode() {
        UInt64.MAX_VALUE.hashCode() shouldBe Long.MAX_VALUE.hashCode()
    }

    @Test
    fun testCompare() {
        UInt64.MAX_VALUE.compareTo(UInt64.MIN_VALUE) shouldBe 1
        34455666666666.toUInt64().compareTo(34455666666666.toUInt64()) shouldBe 0
        14444666666666.toUInt64().compareTo(34455666666666.toUInt64()) shouldBe -1
    }

    @Test
    fun testStringConversion() {
        UInt64.MIN_VALUE.toString() shouldBe "0x0000000000000000"
        UInt64.MAX_VALUE.toString() shouldBe "0xffffffffffffffff"

        uInt64values.forEach {
            UInt64.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        uInt64values.forEach {
            UInt64.writeBytes(it, bc::reserve, bc::write)
            UInt64.fromByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }
}