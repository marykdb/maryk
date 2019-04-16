package maryk.json

import maryk.test.shouldThrow
import kotlin.test.Test

internal abstract class AbstractJsonWriterTest {
    abstract fun createJsonWriter(writer: (String) -> Unit): IsJsonLikeWriter

    @Test
    fun notStartWithUnallowedJSONTypes() {
        var output = ""
        createJsonWriter {
            output += it
        }.apply {
            // Should not be able to start with end object
            shouldThrow<IllegalJsonOperation> {
                writeEndObject()
            }

            // Should not be able to start with end array
            shouldThrow<IllegalJsonOperation> {
                writeEndArray()
            }

            // Should not be able to start with value
            shouldThrow<IllegalJsonOperation> {
                writeValue("test")
            }

            // Should not be able to start with string value
            shouldThrow<IllegalJsonOperation> {
                writeString("test")
            }

            // Should not be able to start with field name
            shouldThrow<IllegalJsonOperation> {
                writeFieldName("test")
            }
        }
    }

    @Test
    fun notAllowIllegalOperationsInsideAnArray() {
        var output = ""
        createJsonWriter {
            output += it
        }.apply {
            writeStartArray()

            // Should not be able to write end object after start array
            shouldThrow<IllegalJsonOperation> {
                writeEndObject()
            }

            // Should not be able to write fieldname to array
            shouldThrow<IllegalJsonOperation> {
                writeFieldName("test")
            }
        }
    }


    @Test
    fun notAllowIllegalOperationsInsideAnObject() {
        var output = ""
        createJsonWriter {
            output += it
        }.apply {
            writeStartObject()

            // Should not be able to write end array after start object
            shouldThrow<IllegalJsonOperation> {
                writeEndArray()
            }

            // Should not be able to write value before a field name
            shouldThrow<IllegalJsonOperation> {
                writeValue("false")
            }

            // Should not be able to write string value before a field name
            shouldThrow<IllegalJsonOperation> {
                writeString("test")
            }
        }
    }

    @Test
    fun notAllowIllegalOperationsInsideAnObjectFieldName() {
        var output = ""
        createJsonWriter {
            output += it
        }.apply {
            writeStartObject()
            writeFieldName("field")

            // Should not be able to write end array after field name
            shouldThrow<IllegalJsonOperation> {
                writeEndArray()
            }

            // Should not be able to write end object after field name
            shouldThrow<IllegalJsonOperation> {
                writeEndObject()
            }

            // Should not be able to write field name after field name
            shouldThrow<IllegalJsonOperation> {
                writeFieldName("anotherField")
            }
        }
    }
}
