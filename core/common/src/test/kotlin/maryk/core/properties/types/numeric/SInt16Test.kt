package maryk.core.properties.types.numeric

import maryk.core.properties.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class SInt16Test {
    private val int16values = shortArrayOf(
        Short.MIN_VALUE,
        -839,
        0,
        12312,
        Short.MAX_VALUE
    )

    @Test
    fun testRandom() {
        SInt16.createRandom()
    }

    @Test
    fun testStringConversion() {
        Short.MIN_VALUE.toString() shouldBe "-32768"
        Short.MAX_VALUE.toString() shouldBe "32767"

        for (it in int16values) {
            SInt16.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        for (it in int16values) {
            bc.reserve(SInt16.size)
            SInt16.writeStorageBytes(it, bc::write)
            SInt16.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        for (it in int16values) {
            bc.reserve(SInt16.calculateTransportByteLength(it))
            SInt16.writeTransportBytes(it, bc::write)
            SInt16.readTransportBytes(bc::read) shouldBe it
            bc.reset()
        }
    }


    @Test
    fun test_of_native_types() {
        SInt16.ofLong(12345) shouldBe 12345.toShort()
        SInt16.ofDouble(12.0) shouldBe 12.toShort()
        SInt16.ofInt(12) shouldBe 12.toShort()
    }

    @Test
    fun test_is_of_type() {
        SInt16.isOfType(12.toShort()) shouldBe true
        SInt16.isOfType(1234L) shouldBe false
    }
}
