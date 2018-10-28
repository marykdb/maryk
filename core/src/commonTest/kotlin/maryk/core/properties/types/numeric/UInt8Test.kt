@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.properties.types.numeric

import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class UInt8Test {
    private val uInt8values = arrayOf(UInt8.MIN_VALUE, UInt8.MAX_VALUE, 89.toUByte(), 127.toUByte())

    @Test
    fun testRandom() {
        UInt8.createRandom()
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
}
