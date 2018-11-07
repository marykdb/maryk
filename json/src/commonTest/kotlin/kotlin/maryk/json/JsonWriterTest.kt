package maryk.json

import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class JsonWriterTest {
    @Test
    fun writeExpectedJSON() {
        var output = ""
        val writer = { string: String -> output += string }

        val generator = JsonWriter(writer = writer)

        writeJson(generator)

        output shouldBe """[1,"#Test",3.5,true,{"test":false,"test2":"value"},{"another":"yes","null":null}]"""
    }

    @Test
    fun writeExpectedPrettyJSON() {
        var output = ""
        val writer = { string: String -> output += string }

        val generator = JsonWriter(pretty = true, writer = writer)

        writeJson(generator)

        output shouldBe """[1, "#Test", 3.5, true, {
                        |	"test": false,
                        |	"test2": "value"
                        |}, {
                        |	"another": "yes",
                        |	"null": null
                        |}]""".trimMargin()
    }

    private fun writeJson(writer: IsJsonLikeWriter) {
        writer.writeStartArray()
        writer.writeInt(1)
        writer.writeString("#Test")
        writer.writeFloat(3.5f)
        writer.writeValue("true")
        writer.writeStartObject()
        writer.writeFieldName("test")
        writer.writeBoolean(false)
        writer.writeFieldName("test2")
        writer.writeString("value")
        writer.writeEndObject()
        writer.writeStartObject()
        writer.writeFieldName("another")
        writer.writeString("yes")
        writer.writeFieldName("null")
        writer.writeNull()
        writer.writeEndObject()
        writer.writeEndArray()
    }

    @Test
    fun notStartWithUnallowedJSONTypes() {
        var output = ""

        val jsonWriter = JsonWriter {
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

        // Should not be able to start with field name
        shouldThrow<IllegalJsonOperation> {
            jsonWriter.writeFieldName("test")
        }
    }

    @Test
    fun notAllowIllegalOperationsInsideAnArray() {
        var output = ""
        val jsonWriter = JsonWriter {
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
        val jsonWriter = JsonWriter {
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
        val jsonWriter = JsonWriter {
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
