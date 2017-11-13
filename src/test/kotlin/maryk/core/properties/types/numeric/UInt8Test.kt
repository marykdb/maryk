package maryk.core.properties.types.numeric

import maryk.core.properties.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class UInt8Test {
    private val uInt8values = arrayOf(UInt8.MIN_VALUE, UInt8.MAX_VALUE, 89.toUInt8(), 127.toByte().toUInt8())

    @Test
    fun testRandom() {
        UInt8.createRandom()
    }

    @Test
    fun testHashCode() {
        UInt8.MAX_VALUE.hashCode() shouldBe Byte.MAX_VALUE.hashCode()
    }

    @Test
    fun testCompare() {
        UInt8.MAX_VALUE.compareTo(UInt8.MIN_VALUE) shouldBe 1
        3.toUInt8().compareTo(3.toUInt8()) shouldBe 0
        1.toUInt8().compareTo(3.toUInt8()) shouldBe -1
    }

    @Test
    fun testStringConversion() {
        UInt8.MIN_VALUE.toString() shouldBe "0"
        UInt8.MAX_VALUE.toString() shouldBe "255"

        uInt8values.forEach {
            UInt8.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        uInt8values.forEach {
            bc.reserve(UInt8.size)
            UInt8.writeStorageBytes(it, bc::write)
            UInt8.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        uInt8values.forEach {
            bc.reserve(UInt8.calculateTransportByteLength(it))
            UInt8.writeTransportBytes(it, bc::write)
            UInt8.readTransportBytes(bc::read) shouldBe it
            bc.reset()
        }
    }
}