package maryk.yaml

import maryk.json.IllegalJsonOperation
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

private const val YAML_OUTPUT = """- 1
- '#Test'
- 3.5
- true
- test: false
  test2: value
- another: yes
"""

internal class YamlWriterTest {
    @Test
    fun write_expected_YAML() {
        var output = ""
        val writer = YamlWriter { string: String -> output += string }

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

        output shouldBe YAML_OUTPUT
    }

    @Test
    fun write_YAML_in_double_array() {
        var output = ""
        val writer = { string: String -> output += string }
        val generator = YamlWriter(writer = writer)

        generator.writeStartArray()
        generator.writeStartArray()
        generator.writeValue("1")
        generator.writeValue("2")
        generator.writeEndArray()
        generator.writeStartArray()
        generator.writeValue("3")
        generator.writeValue("4")
        generator.writeEndArray()
        generator.writeEndArray()

        output shouldBe """
        |-
        |  - 1
        |  - 2
        |-
        |  - 3
        |  - 4
        |""".trimMargin()
    }

    @Test
    fun not_start_with_unallowed_JSON_types() {
        var output = ""

        val jsonWriter = YamlWriter {
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
        val jsonWriter = YamlWriter {
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
        val jsonWriter = YamlWriter {
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
        val jsonWriter = YamlWriter {
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
