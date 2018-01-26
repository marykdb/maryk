package maryk.core.properties.types.numeric

import maryk.core.properties.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

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

        for (it in uInt64values) {
            UInt64.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        for (it in uInt64values) {
            bc.reserve(UInt64.size)
            UInt64.writeStorageBytes(it, bc::write)
            UInt64.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        for (it in uInt64values) {
            bc.reserve(UInt64.calculateTransportByteLength(it))
            UInt64.writeTransportBytes(it, bc::write)
            UInt64.readTransportBytes(bc::read) shouldBe it
            bc.reset()
        }
    }
}