package maryk.core.properties.types.numeric

import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class UInt64Test {
    private val uInt64values = arrayOf(
        UInt64.MIN_VALUE,
        UInt64.MAX_VALUE,
        6267862346434742349L.toUInt64(),
        0L.toUInt64()
    )

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
        UInt64.MIN_VALUE.toString() shouldBe "0"
        UInt64.MAX_VALUE.toString() shouldBe "0xffffffffffffffff"
        Long.MAX_VALUE.toUInt64().toString() shouldBe "9223372036854775807"

        UInt64.ofString("0xf000000000000000").toString() shouldBe "0xf000000000000000"

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

    @Test
    fun test_int_conversion() {
        12345678L.toUInt64().toInt() shouldBe 12345678
    }
}
