package maryk.core.properties.types

import maryk.core.properties.definitions.DateTimeDefinition
import maryk.test.models.TestValueObject
import kotlin.test.Test
import kotlin.test.expect

internal class ValueDataObjectTest {
    private val value = TestValueObject(
        int = 4,
        dateTime = DateTimeDefinition.nowUTC().roundToDateUnit(DateUnit.Seconds),
        bool = true
    )

    private val value2 = TestValueObject(
        int = 5,
        dateTime = DateTimeDefinition.nowUTC().roundToDateUnit(DateUnit.Seconds),
        bool = false
    )

    @Test
    fun testCompareToBytes() {
        expect(0) { value compareTo value }
        expect(-1) { value compareTo value2 }
        expect(1) { value2 compareTo value }
    }

    @Test
    fun testConvertBytes() {
        val bytes = value.toByteArray()
        val new = TestValueObject.Serializer.readFromBytes(bytes.iterator()::nextByte)

        expect(0) { new compareTo value }
    }

    @Test
    fun testConvertString() {
        val string = value.toBase64()
        val new = TestValueObject.Serializer.fromBase64(string)

        expect(0) { new compareTo value }
    }
}
