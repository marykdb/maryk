package maryk.core.json

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import org.junit.Test

internal class JsonGeneratorTest {
    @Test
    fun testJsonGeneration() {
        var output = ""
        val writer = { string: String -> output += string }

        val generator = JsonGenerator(writer = writer)

        generateJson(generator)

        output shouldBe "[1,\"Test\",3.5,true,{\"test\":false,\"test2\":\"value\"}]"
    }

    @Test
    fun testPrettyJsonGeneration() {
        var output = ""
        val writer = { string: String -> output += string }

        val generator = JsonGenerator(pretty = true, writer = writer)

        generateJson(generator)

        output shouldBe "[1, \"Test\", 3.5, true, {\n" +
                "\t\"test\": false,\n" +
                "\t\"test2\": \"value\"\n" +
                "}]"
    }

    @Test
    fun testWrongJsonStartGeneration() {
        var output = ""

        val generator = JsonGenerator {
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
        val generator = JsonGenerator {
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
        val generator = JsonGenerator {
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
        val generator = JsonGenerator {
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

    private fun generateJson(generator: JsonGenerator) {
        generator.writeStartArray()
        generator.writeValue("1")
        generator.writeString("Test")
        generator.writeValue("3.5")
        generator.writeValue("true")
        generator.writeStartObject()
        generator.writeFieldName("test")
        generator.writeValue("false")
        generator.writeFieldName("test2")
        generator.writeString("value")
        generator.writeEndObject()
        generator.writeEndArray()
    }
}