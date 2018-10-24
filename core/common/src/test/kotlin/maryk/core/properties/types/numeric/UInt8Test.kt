package maryk.core.properties.types.numeric

import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
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

        for (it in uInt8values) {
            UInt8.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()

        val values = uInt8values.zip(
            arrayOf("00", "ff", "59", "7f")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt8.size)
            UInt8.writeStorageBytes(value, bc::write)

            bc.bytes?.toHex() shouldBe hexString

            UInt8.fromStorageByteReader(bc.size, bc::read) shouldBe value
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()

        val values = uInt8values.zip(
            arrayOf("00", "ff01", "59", "7f")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt8.calculateTransportByteLength(value))
            UInt8.writeTransportBytes(value, bc::write)

            bc.bytes?.toHex() shouldBe hexString

            UInt8.readTransportBytes(bc::read) shouldBe value
            bc.reset()
        }
    }

    @Test
    fun test_int_conversion() {
        12.toUInt8().toInt() shouldBe 12
    }
}
