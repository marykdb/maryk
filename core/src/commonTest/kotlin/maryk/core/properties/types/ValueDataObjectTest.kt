package maryk.core.properties.types

import maryk.core.properties.definitions.DateTimeDefinition
import maryk.test.models.TestValueObject
import kotlin.test.Test
import kotlin.test.expect
import kotlin.test.assertContentEquals

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
        expect(value.hashCode()) { new.hashCode() }
    }

    @Test
    fun byteArrayConversionIsDefensive() {
        val bytes = value.toByteArray()
        val originalBytes = bytes.copyOf()

        bytes[0] = (bytes[0].toInt() xor 0xFF).toByte()

        assertContentEquals(originalBytes, value.toByteArray())
    }

    @Test
    fun testConvertString() {
        val string = value.toBase64()
        val new = TestValueObject.Serializer.fromBase64(string)

        expect(0) { new compareTo value }
    }
}
