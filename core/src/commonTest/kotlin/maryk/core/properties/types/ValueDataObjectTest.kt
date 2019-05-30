package maryk.core.properties.types

import maryk.lib.time.Time
import maryk.test.models.TestValueObject
import kotlin.test.Test
import kotlin.test.expect

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
        expect(0) { value.compareTo(value) }
        expect(-1) { value.compareTo(value2) }
        expect(1) { value2.compareTo(value) }
    }

    @Test
    fun testConvertBytes() {
        val bytes = value.toByteArray()
        val new = TestValueObject.readFromBytes(bytes.iterator()::nextByte)

        expect(0) { new.compareTo(value) }
    }

    @Test
    fun testConvertString() {
        val string = value.toBase64()
        val new = TestValueObject.fromBase64(string)

        expect(0) { new.compareTo(value) }
    }
}
