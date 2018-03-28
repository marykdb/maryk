package maryk.core.properties.types.numeric

import maryk.core.properties.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class SInt32Test {
    private val int32values = intArrayOf(
        Int.MIN_VALUE,
        -424234234,
        0,
        424234234,
        Int.MAX_VALUE
    )

    @Test
    fun testRandom() {
        SInt32.createRandom()
    }

    @Test
    fun testStringConversion() {
        Int.MIN_VALUE.toString() shouldBe "-2147483648"
        Int.MAX_VALUE.toString() shouldBe "2147483647"

        for (it in int32values) {
            SInt32.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        for (it in int32values) {
            bc.reserve(SInt32.size)
            SInt32.writeStorageBytes(it, bc::write)
            SInt32.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        for (it in int32values) {
            bc.reserve(SInt32.calculateTransportByteLength(it))
            SInt32.writeTransportBytes(it, bc::write)
            SInt32.readTransportBytes(bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun test_of_native_types() {
        SInt32.ofLong(123) shouldBe 123
        SInt32.ofDouble(12.0) shouldBe 12
        SInt32.ofInt(12) shouldBe 12
    }

    @Test
    fun test_is_of_type() {
        SInt32.isOfType(12) shouldBe true
        SInt32.isOfType(1234L) shouldBe false
    }
}
