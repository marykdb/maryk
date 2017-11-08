package maryk.core.json

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import org.junit.Test

internal class JsonWriterTest {
    @Test
    fun testJsonGeneration() {
        var output = ""
        val writer = { string: String -> output += string }

        val generator = JsonWriter(writer = writer)

        generateJson(generator)

        output shouldBe "[1,\"Test\",3.5,true,{\"test\":false,\"test2\":\"value\"},{\"another\":\"yes\"}]"
    }

    @Test
    fun testPrettyJsonGeneration() {
        var output = ""
        val writer = { string: String -> output += string }

        val generator = JsonWriter(pretty = true, writer = writer)

        generateJson(generator)

        output shouldBe "[1, \"Test\", 3.5, true, {\n" +
                "\t\"test\": false,\n" +
                "\t\"test2\": \"value\"\n" +
                "}, {\n" +
                "\t\"another\": \"yes\"\n" +
                "}]"
    }

    @Test
    fun testWrongJsonStartGeneration() {
        var output = ""

        val generator = JsonWriter {
            output += it
        }

        // Should not be able to start with end object
        shouldThrow<IllegalJsonOperation> {
            generator.writeEndObject()
        }

        // Should not be able to start with end array
        shouldThrow<IllegalJsonOperation> {
            generator.writeEndArray()
        }

        // Should not be able to start with value
        shouldThrow<IllegalJsonOperation> {
            generator.writeValue("test")
        }

        // Should not be able to start with string value
        shouldThrow<IllegalJsonOperation> {
            generator.writeString("test")
        }

        // Should not be able to start with field name
        shouldThrow<IllegalJsonOperation> {
            generator.writeFieldName("test")
        }
    }

    @Test
    fun testWrongJsonArrayGeneration() {
        var output = ""
        val generator = JsonWriter {
            output += it
        }

        generator.writeStartArray()

        // Should not be able to write end object after start array
        shouldThrow<IllegalJsonOperation> {
            generator.writeEndObject()
        }

        // Should not be able to write fieldname to array
        shouldThrow<IllegalJsonOperation> {
            generator.writeFieldName("test")
        }
    }


    @Test
    fun testWrongJsonObjectGeneration() {
        var output = ""
        val generator = JsonWriter {
            output += it
        }

        generator.writeStartObject()

        // Should not be able to write end array after start object
        shouldThrow<IllegalJsonOperation> {
            generator.writeEndArray()
        }

        // Should not be able to write value before a fieldname
        shouldThrow<IllegalJsonOperation> {
            generator.writeValue("false")
        }

        // Should not be able to write string value before a fieldname
        shouldThrow<IllegalJsonOperation> {
            generator.writeString("test")
        }
    }

    @Test
    fun testWrongJsonObjectAfterFieldNameGeneration() {
        var output = ""
        val generator = JsonWriter {
            output += it
        }

        generator.writeStartObject()
        generator.writeFieldName("field")

        // Should not be able to write end array after fieldname
        shouldThrow<IllegalJsonOperation> {
            generator.writeEndArray()
        }

        // Should not be able to write end object after fieldname
        shouldThrow<IllegalJsonOperation> {
            generator.writeEndObject()
        }

        // Should not be able to write field name after field name
        shouldThrow<IllegalJsonOperation> {
            generator.writeFieldName("anotherField")
        }
    }

    private fun generateJson(writer: JsonWriter) {
        writer.writeStartArray()
        writer.writeValue("1")
        writer.writeString("Test")
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
}