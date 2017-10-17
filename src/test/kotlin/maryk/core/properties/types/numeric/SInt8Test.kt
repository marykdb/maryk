package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test

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

        int8values.forEach {
            SInt8.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        int8values.forEach {
            SInt8.writeStorageBytes(it, bc::reserve, bc::write)
            SInt8.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        int8values.forEach {
            SInt8.writeTransportBytes(it, bc::reserve, bc::write)
            SInt8.readTransportBytes(bc::read) shouldBe it
            bc.reset()
        }
    }
}