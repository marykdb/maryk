package maryk.core.properties.types

import io.kotlintest.matchers.shouldBe
import maryk.TestValueObject
import org.junit.Test

internal class ValueDataObjectTest {
    private val value = TestValueObject(
            int = 4,
            dateTime = DateTime(date = Date.nowUTC(), time = Time.nowUTC().copy(milli = 0)),
            bool = true
    )

    private val value2 = TestValueObject(
            int = 5,
            dateTime = DateTime(date = Date.nowUTC(), time = Time.nowUTC().copy(milli = 0)),
            bool = false
    )


    @Test
    fun testCompareToBytes() {
        value.compareTo(value) shouldBe 0
        value.compareTo(value2) shouldBe -1
        value2.compareTo(value) shouldBe 1
    }

    @Test
    fun testConvertBytes() {
        val bytes = value._bytes
        val new = TestValueObject.readFromBytes(bytes.iterator()::nextByte)

        new.compareTo(value) shouldBe 0
    }

    @Test
    fun testConvertString() {
        val string = value.toBase64()
        val new = TestValueObject.fromString(string)

        new.compareTo(value) shouldBe 0
    }
}