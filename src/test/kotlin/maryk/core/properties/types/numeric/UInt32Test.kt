package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test

internal class UInt32Test {

    private val uInt32values = arrayOf(UInt32.MIN_VALUE, UInt32.MAX_VALUE, 4242342349.toUInt32())

    @Test
    fun testRandom() {
        UInt32.createRandom()
    }

    @Test
    fun testHashCode() {
        UInt32.MAX_VALUE.hashCode() shouldBe Integer.MAX_VALUE.hashCode()
    }

    @Test
    fun testCompare() {
        UInt32.MAX_VALUE.compareTo(UInt32.MIN_VALUE) shouldBe 1
        34455.toUInt32().compareTo(34455.toUInt32()) shouldBe 0
        14444.toUInt32().compareTo(34455.toUInt32()) shouldBe -1
    }

    @Test
    fun testStringConversion() {
        UInt32.MIN_VALUE.toString() shouldBe "0"
        UInt32.MAX_VALUE.toString() shouldBe "4294967295"

        uInt32values.forEach {
            UInt32.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        uInt32values.forEach {
            UInt32.writeStorageBytes(it, bc::reserve, bc::write)
            UInt32.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        uInt32values.forEach {
            bc.reserve(UInt32.calculateTransportByteLength(it))
            UInt32.writeTransportBytes(it, bc::write)
            UInt32.readTransportBytes(bc::read) shouldBe it
            bc.reset()
        }
    }
}