package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test

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

        int16values.forEach {
            SInt16.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        int16values.forEach {
            SInt16.writeStorageBytes(it, bc::reserve, bc::write)
            SInt16.fromStorageByteReader(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        int16values.forEach {
            bc.reserve(SInt16.calculateTransportByteSize(it))
            SInt16.writeTransportBytes(it, bc::write)
            SInt16.readTransportBytes(bc::read) shouldBe it
            bc.reset()
        }
    }
}