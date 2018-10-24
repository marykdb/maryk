@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.properties.types.numeric

import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class UInt64Test {
    private val uInt64values = arrayOf(
        UInt64.MIN_VALUE,
        UInt64.MAX_VALUE,
        6267862346434742349uL,
        0uL
    )

    @Test
    fun testRandom() {
        UInt64.createRandom()
    }

    @Test
    fun testStringConversion() {
        UInt64.MIN_VALUE.toString() shouldBe "0"
        UInt64.MAX_VALUE.toString() shouldBe "18446744073709551615"
        Long.MAX_VALUE.toULong().toString() shouldBe "9223372036854775807"

        UInt64.ofString("17293822569102704640").toString() shouldBe "17293822569102704640"

        for (it in uInt64values) {
            UInt64.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()

        val values = uInt64values.zip(
            arrayOf("0000000000000000", "ffffffffffffffff", "56fbeb98744a084d", "0000000000000000")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt64.size)
            UInt64.writeStorageBytes(value, bc::write)

            bc.bytes?.toHex() shouldBe hexString

            UInt64.fromStorageByteReader(bc.size, bc::read) shouldBe value
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()

        val values = uInt64values.zip(
            arrayOf("00", "ffffffffffffffffff01", "cd90a8a287f3fafd56", "00")
        )

        for ((value, hexString) in values) {
            bc.reserve(UInt64.calculateTransportByteLength(value))
            UInt64.writeTransportBytes(value, bc::write)

            bc.bytes?.toHex() shouldBe hexString

            UInt64.readTransportBytes(bc::read) shouldBe value
            bc.reset()
        }
    }
}
