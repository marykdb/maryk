package maryk.core.properties

import maryk.core.models.SimpleObjectModel
import maryk.core.properties.definitions.string
import maryk.core.properties.exceptions.PropertyConversionDirection
import maryk.core.properties.exceptions.PropertyConversionException
import maryk.core.values.ObjectValues
import maryk.core.values.convertToCurrentValue
import maryk.json.JsonWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private data class ConvertedValue(val raw: String)

private data class ConversionObject(val value: ConvertedValue) {
    companion object : SimpleObjectModel<ConversionObject, Companion>() {
        val value by string(
            index = 1u,
            getter = ConversionObject::value,
            toSerializable = { _: ConvertedValue?, _: IsPropertyContext? ->
                throw ClassCastException("to-serializable failed")
            },
            fromSerializable = { _: String? ->
                throw ClassCastException("from-serializable failed")
            },
        )

        override fun invoke(values: ObjectValues<ConversionObject, Companion>) = ConversionObject(
            value = values(1u),
        )
    }
}

class PropertyConversionTest {
    @Test
    fun reportsToSerializableFailureWithPropertyContext() {
        val exception = assertFailsWith<PropertyConversionException> {
            ConversionObject.create {
                value with ConvertedValue("input")
            }
        }

        assertEquals("value", exception.propertyName)
        assertEquals(PropertyConversionDirection.TO_SERIALIZABLE, exception.direction)
        assertTrue(exception.inputType.contains("ConvertedValue"))
        assertEquals("to-serializable failed", assertIs<ClassCastException>(exception.cause).message)
    }

    @Test
    fun reportsToSerializableFailureFromObjectSerialization() {
        val exception = assertFailsWith<PropertyConversionException> {
            val writer = JsonWriter {}
            ConversionObject.Serializer.writeObjectAsJson(ConversionObject(ConvertedValue("input")), writer)
        }

        assertEquals("value", exception.propertyName)
        assertEquals(PropertyConversionDirection.TO_SERIALIZABLE, exception.direction)
        assertTrue(exception.inputType.contains("ConvertedValue"))
        assertEquals("to-serializable failed", assertIs<ClassCastException>(exception.cause).message)
    }

    @Test
    fun reportsFromSerializableFailureWithPropertyContext() {
        val exception = assertFailsWith<PropertyConversionException> {
            ConversionObject.value.convertToCurrentValue<String, ConvertedValue>("serialized")
        }

        assertEquals("value", exception.propertyName)
        assertEquals(PropertyConversionDirection.FROM_SERIALIZABLE, exception.direction)
        assertTrue(exception.inputType.contains("String"))
        assertEquals("from-serializable failed", assertIs<ClassCastException>(exception.cause).message)
    }
}
