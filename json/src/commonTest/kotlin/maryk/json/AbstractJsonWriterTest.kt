package maryk.json

import maryk.test.shouldThrow
import kotlin.test.Test

internal abstract class AbstractJsonWriterTest {
    abstract fun createJsonWriter(writer: (String) -> Unit): IsJsonLikeWriter

    @Test
    fun notStartWithUnallowedJSONTypes() {
        var output = ""

        val jsonWriter = createJsonWriter {
            output += it
        }

        // Should not be able to start with end object
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeEndObject()
        }

        // Should not be able to start with end array
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeEndArray()
        }

        // Should not be able to start with value
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeValue("test")
        }

        // Should not be able to start with string value
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeString("test")
        }

        // Should not be able to start with field name
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeFieldName("test")
        }
    }

    @Test
    fun notAllowIllegalOperationsInsideAnArray() {
        var output = ""
        val jsonWriter = createJsonWriter {
            output += it
        }

        jsonWriter.writeStartArray()

        // Should not be able to write end object after start array
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeEndObject()
        }

        // Should not be able to write fieldname to array
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeFieldName("test")
        }
    }


    @Test
    fun notAllowIllegalOperationsInsideAnObject() {
        var output = ""
        val jsonWriter = createJsonWriter {
            output += it
        }

        jsonWriter.writeStartObject()

        // Should not be able to write end array after start object
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeEndArray()
        }

        // Should not be able to write value before a fieldname
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeValue("false")
        }

        // Should not be able to write string value before a fieldname
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeString("test")
        }
    }

    @Test
    fun notAllowIllegalOperationsInsideAnObjectFieldName() {
        var output = ""
        val jsonWriter = createJsonWriter {
            output += it
        }

        jsonWriter.writeStartObject()
        jsonWriter.writeFieldName("field")

        // Should not be able to write end array after fieldname
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeEndArray()
        }

        // Should not be able to write end object after fieldname
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeEndObject()
        }

        // Should not be able to write field name after field name
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeFieldName("anotherField")
        }
    }
}
