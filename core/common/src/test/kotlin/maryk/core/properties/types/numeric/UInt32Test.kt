@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")
package maryk.core.properties.types.numeric

import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class UInt32Test {

    private val uInt32values = arrayOf(UInt32.MIN_VALUE, UInt32.MAX_VALUE, 4242342349u)

    @Test
    fun testRandom() {
        UInt32.createRandom()
    }

    @Test
    fun testStringConversion() {
        UInt32.MIN_VALUE.toString() shouldBe "0"
        UInt32.MAX_VALUE.toString() shouldBe "4294967295"

        for (it in uInt32values) {
            UInt32.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()

        val values = uInt32values.zip(
            arrayOf("00000000", "ffffffff", "fcdd01cd")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt32.size)
            UInt32.writeStorageBytes(value, bc::write)

            bc.bytes?.toHex() shouldBe hexString

            UInt32.fromStorageByteReader(bc.size, bc::read) shouldBe value
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()

        val values = uInt32values.zip(
            arrayOf("00", "ffffffff0f", "cd83f4e60f")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt32.calculateTransportByteLength(value))
            UInt32.writeTransportBytes(value, bc::write)

            bc.bytes?.toHex() shouldBe hexString

            UInt32.readTransportBytes(bc::read) shouldBe value
            bc.reset()
        }
    }
}
