package maryk.core.properties.types.numeric

import io.kotlintest.matchers.shouldBe
import org.junit.Test

internal class UInt8Test {
    private val uInt8values = arrayOf(UInt8.MIN_VALUE, UInt8.MAX_VALUE, 89.toUInt8(), 127.toByte().toUInt8())

    @Test
    fun testRandom() {
        UInt8.createRandom()
    }

    @Test
    fun testHashCode() {
        UInt8.MAX_VALUE.hashCode() shouldBe Byte.MAX_VALUE.hashCode()
    }

    @Test
    fun testCompare() {
        UInt8.MAX_VALUE.compareTo(UInt8.MIN_VALUE) shouldBe 1
        3.toUInt8().compareTo(3.toUInt8()) shouldBe 0
        1.toUInt8().compareTo(3.toUInt8()) shouldBe -1
    }

    @Test
    fun test8StringConversion() {
        UInt8.MIN_VALUE.toString() shouldBe "0"
        UInt8.MAX_VALUE.toString() shouldBe "255"

        uInt8values.forEach {
            UInt8.ofString(it.toString()) shouldBe it
        }
    }

    @Test
    fun testInt8BytesConversion() {
        val bytes = ByteArray(33)

        uInt8values.forEach {
            UInt8.ofBytes(it.toBytes()) shouldBe it
            UInt8.ofBytes(it.toBytes(bytes, 10), 10) shouldBe it
            UInt8.ofBytes(UInt8.toBytes(it, bytes, 10), 10) shouldBe it
        }
    }
}