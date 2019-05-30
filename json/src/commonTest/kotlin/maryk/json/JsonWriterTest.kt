package maryk.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class JsonWriterTest {
    @Test
    fun writeExpectedJSON() {
        var output = ""
        val writer = { string: String -> output += string }

        val generator = JsonWriter(writer = writer)

        writeJson(generator)

        assertEquals(
            """[1,"#Test",3.5,true,{"test":false,"test2":"value"},{"another":"yes","null":null}]""",
            output
        )
    }

    @Test
    fun writeExpectedPrettyJSON() {
        var output = ""
        val writer = { string: String -> output += string }

        val generator = JsonWriter(pretty = true, writer = writer)

        writeJson(generator)

        assertEquals(
            """
            [1, "#Test", 3.5, true, {
              "test": false,
              "test2": "value"
            }, {
              "another": "yes",
              "null": null
            }]""".trimIndent(),
            output
        )
    }

    private fun writeJson(writer: IsJsonLikeWriter) {
        writer.apply {
            writeStartArray()
            writeInt(1)
            writeString("#Test")
            writeFloat(3.5f)
            writeValue("true")
            writeStartObject()
            writeFieldName("test")
            writeBoolean(false)
            writeFieldName("test2")
            writeString("value")
            writeEndObject()
            writeStartObject()
            writeFieldName("another")
            writeString("yes")
            writeFieldName("null")
            writeNull()
            writeEndObject()
            writeEndArray()
        }
    }

    @Test
    fun notStartWithUnallowedJSONTypes() {
        var output = ""

        JsonWriter {
            output += it
        }.apply {
            // Should not be able to start with end object
            assertFailsWith<IllegalJsonOperation> {
                writeEndObject()
            }

            // Should not be able to start with end array
            assertFailsWith<IllegalJsonOperation> {
                writeEndArray()
            }

            // Should not be able to start with field name
            assertFailsWith<IllegalJsonOperation> {
                writeFieldName("test")
            }
        }
    }

    @Test
    fun notAllowIllegalOperationsInsideAnArray() {
        var output = ""

        JsonWriter {
            output += it
        }.apply {
            writeStartArray()

            // Should not be able to write end object after start array
            assertFailsWith<IllegalJsonOperation> {
                writeEndObject()
            }

            // Should not be able to write field name to array
            assertFailsWith<IllegalJsonOperation> {
                writeFieldName("test")
            }
        }
    }


    @Test
    fun notAllowIllegalOperationsInsideAnObject() {
        var output = ""

        JsonWriter {
            output += it
        }.apply {
            writeStartObject()

            // Should not be able to write end array after start object
            assertFailsWith<IllegalJsonOperation> {
                writeEndArray()
            }

            // Should not be able to write value before a field name
            assertFailsWith<IllegalJsonOperation> {
                writeValue("false")
            }

            // Should not be able to write string value before a field name
            assertFailsWith<IllegalJsonOperation> {
                writeString("test")
            }
        }

    }

    @Test
    fun notAllowIllegalOperationsInsideAnObjectFieldName() {
        var output = ""

        JsonWriter {
            output += it
        }.apply {
            writeStartObject()
            writeFieldName("field")

            // Should not be able to write end array after field name
            assertFailsWith<IllegalJsonOperation> {
                writeEndArray()
            }

            // Should not be able to write end object after field name
            assertFailsWith<IllegalJsonOperation> {
                writeEndObject()
            }

            // Should not be able to write field name after field name
            assertFailsWith<IllegalJsonOperation> {
                writeFieldName("anotherField")
            }
        }

    }
}
