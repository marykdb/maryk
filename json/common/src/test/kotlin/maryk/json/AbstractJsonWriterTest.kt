package maryk.json

import maryk.test.shouldThrow
import kotlin.test.Test

internal abstract class AbstractJsonWriterTest {
    abstract fun createJsonWriter(writer: (String) -> Unit): IsJsonLikeWriter

    internal fun writeJson(writer: IsJsonLikeWriter) {
        writer.writeStartArray()
        writer.writeValue("1")
        writer.writeString("#Test")
        writer.writeValue("3.5")
        writer.writeValue("true")
        writer.writeStartObject()
        writer.writeFieldName("test")
        writer.writeValue("false")
        writer.writeFieldName("test2")
        writer.writeString("value")
        writer.writeEndObject()
        writer.writeStartObject()
        writer.writeFieldName("another")
        writer.writeString("yes")
        writer.writeEndObject()
        writer.writeEndArray()
    }

    @Test
    fun not_start_with_unallowed_JSON_types() {
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
    fun not_allow_illegal_operations_inside_an_Array() {
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
    fun not_allow_illegal_operations_within_an_Object() {
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
    fun not_allow_illegal_operations_after_an_object_field_name() {
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
