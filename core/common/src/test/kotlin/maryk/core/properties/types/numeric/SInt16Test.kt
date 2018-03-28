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
}