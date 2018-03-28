package maryk.core.properties.types.numeric

import maryk.core.properties.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class SInt8Test {
    private val int8values = byteArrayOf(
        Byte.MIN_VALUE,
        -100,
        0,
        89,
        127,
        Byte.MAX_VALUE
    )

    @Test
    fun testRandom() {
        SInt8.createRandom()
    }

    @Test
    fun testStringConversion() {
        Byte.MIN_VALUE.toString() shouldBe "-128"
        Byte.MAX_VALUE.toString() shouldBe "127"

        for (it in int8values) {
            SInt8.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        for (it in int8values) {
            bc.reserve(SInt8.size)
            SInt8.writeStorageBytes(it, bc::write)
            SInt8.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        for (it in int8values) {
            bc.reserve(SInt8.calculateTransportByteLength(it))
            SInt8.writeTransportBytes(it, bc::write)
            SInt8.readTransportBytes(bc::read) shouldBe it
            bc.reset()
        }
    }
}