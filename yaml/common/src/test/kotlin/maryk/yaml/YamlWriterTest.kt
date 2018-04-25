package maryk.yaml

import maryk.json.IllegalJsonOperation
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class YamlWriterTest {
    @Test
    fun write_expected_YAML() {
        var output = ""
        YamlWriter {
            output += it
        }.apply {
            writeStartArray()
            writeValue("1")
            writeString("#Test's")
            writeValue("3.5")
            writeValue("true")
            writeStartObject()
            writeFieldName("test")
            writeValue("false")
            writeFieldName("test2")
            writeString("value")
            writeEndObject()
            writeStartObject()
            writeFieldName("another")
            writeString("yes")
            writeEndObject()
            writeEndArray()
        }

        output shouldBe """
        |- 1
        |- '#Test''s'
        |- 3.5
        |- true
        |- test: false
        |  test2: value
        |- another: yes
        |""".trimMargin()
    }

    @Test
    fun write_YAML_in_map_embedded_in_map() {
        var output = ""
        YamlWriter {
            output += it
        }.apply {
            writeStartObject()
            writeFieldName("t1")
            writeStartObject()
            writeFieldName("c1")
            writeValue("v1")
            writeFieldName("c2")
            writeValue("v2")
            writeEndObject()
            writeFieldName("t2")
            writeStartObject()
            writeFieldName("c3")
            writeValue("v3")
            writeEndObject()
            writeEndObject()
        }

        output shouldBe """
        |t1:
        |  c1: v1
        |  c2: v2
        |t2:
        |  c3: v3
        |""".trimMargin()
    }

    @Test
    fun write_YAML_in_map_with_simple_child_map() {
        var output = ""

        YamlWriter {
            output += it
        }.apply {
            writeStartObject()
            writeFieldName("t1")
            writeStartObject(true)
            writeFieldName("c1")
            writeValue("v1")
            writeFieldName("c2")
            writeValue("v2")
            writeEndObject()
            writeFieldName("t2")
            writeStartObject(true)
            writeFieldName("c3")
            writeValue("v3")
            writeEndObject()
            writeEndObject()
        }

        output shouldBe """
        |t1: {c1: v1, c2: v2}
        |t2: {c3: v3}
        |""".trimMargin()
    }

    @Test
    fun write_YAML_in_simple_embedded_maps() {
        var output = ""
        YamlWriter {
            output += it
        }.apply {
            writeStartObject(true)
            writeFieldName("t1")
            writeStartObject()
            writeFieldName("c1")
            writeValue("v1")
            writeFieldName("c2")
            writeValue("v2")
            writeEndObject()
            writeFieldName("t2")
            writeStartObject()
            writeFieldName("c3")
            writeValue("v3")
            writeEndObject()
            writeEndObject()
        }

        output shouldBe """
        |{t1: {c1: v1, c2: v2}, t2: {c3: v3}}
        |""".trimMargin()
    }

    @Test
    fun write_YAML_in_double_array() {
        var output = ""
        YamlWriter {
            output += it
        }.apply {
            writeStartArray()
            writeStartArray()
            writeValue("1")
            writeValue("2")
            writeEndArray()
            writeStartArray()
            writeValue("3")
            writeValue("4")
            writeEndArray()
            writeEndArray()
        }

        output shouldBe """
        |- - 1
        |  - 2
        |- - 3
        |  - 4
        |""".trimMargin()
    }

    @Test
    fun write_YAML_in_double_array_with_less_complex_child() {
        var output = ""
        YamlWriter{
            output += it
        }.apply {
            writeStartArray()
            writeStartArray(true)
            writeValue("1")
            writeValue("2")
            writeEndArray()
            writeStartArray(true)
            writeValue("3")
            writeValue("4")
            writeEndArray()
            writeEndArray()
        }

        output shouldBe """
        |- [1, 2]
        |- [3, 4]
        |""".trimMargin()
    }

    @Test
    fun write_YAML_in_less_complex_double_array() {
        var output = ""
        YamlWriter {
            output += it
        }.apply {
            writeStartArray(true)
            writeStartArray() // should be automatically simple
            writeValue("1")
            writeValue("2")
            writeEndArray()
            writeStartArray()
            writeValue("3")
            writeValue("4")
            writeEndArray()
            writeEndArray()
        }

        output shouldBe """
        |[[1, 2],[3, 4]]
        |""".trimMargin()
    }

    @Test
    fun write_YAML_with_tags() {
        var output = ""
        YamlWriter {
            output += it
        }.apply {
            writeTag("!!omap")
            writeStartObject()
            writeFieldName("t1")
            writeTag("!!str")
            writeValue("true")
            writeFieldName("t2")
            writeTag("!!set")
            writeStartArray()
            writeTag("!!int")
            writeValue("30")
            writeEndArray()
            writeFieldName("t3")
            writeTag("!!omap")
            writeStartObject()
            writeFieldName("a1")
            writeValue("1")
            writeEndObject()
            writeFieldName("t4")
            writeTag("!!omap")
            writeStartObject(true)
            writeFieldName("a1")
            writeTag("!!int")
            writeValue("1")
            writeEndObject()
            writeFieldName("t5")
            writeTag("!!set")
            writeStartArray(true)
            writeTag("!!int")
            writeValue("30")
            writeEndArray()
            writeEndObject()
        }

        output shouldBe """
        |!!omap
        |t1: !!str true
        |t2: !!set
        |- !!int 30
        |t3: !!omap
        |  a1: 1
        |t4: !!omap {a1: !!int 1}
        |t5: !!set [!!int 30]
        |""".trimMargin()
    }

    @Test
    fun write_YAML_with_tags_in_array() {
        var output = ""
        YamlWriter {
            output += it
        }.apply {
            writeStartObject()
            writeFieldName("key")
            writeStartArray()
            writeTag("!Foo")
            writeStartObject()
            writeFieldName("k1")
            writeValue("30")
            writeEndObject()
            writeTag("!Bar")
            writeStartObject()
            writeFieldName("k2")
            writeValue("40")
            writeEndObject()
            writeEndArray()
            writeEndObject()
        }

        output shouldBe """
        |key:
        |- !Foo
        |  k1: 30
        |- !Bar
        |  k2: 40
        |""".trimMargin()
    }

    @Test
    fun write_YAML_with_complex_fields() {
        var output = ""
        YamlWriter {
            output += it
        }.apply {
            writeStartArray()
            writeStartObject()

            writeStartComplexField()
            writeStartArray()
            writeValue("a1")
            writeValue("a2")
            writeEndArray()
            writeEndComplexField()
            writeValue("value 1")

            writeStartComplexField()
            writeStartObject()
            writeFieldName("f1")
            writeValue("v1")
            writeFieldName("f2")
            writeValue("v2")
            writeEndObject()
            writeEndComplexField()
            writeTag("!tag")
            writeValue("value 2")

            writeStartComplexField()
            writeStartObject(true)
            writeFieldName("f1")
            writeValue("v1")
            writeFieldName("f2")
            writeValue("v2")
            writeEndObject()
            writeEndComplexField()
            writeStartArray()
            writeValue("a1")
            writeValue("a2")
            writeEndArray()

            writeStartComplexField()
            writeStartArray(true)
            writeValue("a1")
            writeValue("a2")
            writeEndArray()
            writeEndComplexField()
            writeStartObject()
            writeFieldName("f1")
            writeValue("v1")
            writeFieldName("f2")
            writeValue("v2")
            writeEndObject()

            writeEndObject()
            writeEndArray()
        }

        output shouldBe """
        |- ? - a1
        |    - a2
        |  : value 1
        |  ? f1: v1
        |    f2: v2
        |  : !tag value 2
        |  ? {f1: v1, f2: v2}
        |  :
        |  - a1
        |  - a2
        |  ? [a1, a2]
        |  : f1: v1
        |    f2: v2
        |""".trimMargin()
    }

    @Test
    fun not_start_with_unallowed_YAML_types() {
        var output = ""

        YamlWriter {
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
    fun not_allow_illegal_operations_inside_an_Array() {
        var output = ""
        YamlWriter {
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
    fun not_allow_illegal_operations_within_an_Object() {
        var output = ""

        YamlWriter {
            output += it
        }.apply {
            writeStartObject()

            // Should not be able to write end array after start object
            shouldThrow<IllegalJsonOperation> {
                writeEndArray()
            }

            // Should not be able to write value before a fieldname
            shouldThrow<IllegalJsonOperation> {
                writeValue("false")
            }

            // Should not be able to write string value before a fieldname
            shouldThrow<IllegalJsonOperation> {
                writeString("test")
            }    
        }
    }

    @Test
    fun not_allow_illegal_operations_after_an_object_field_name() {
        var output = ""
        YamlWriter {
            output += it
        }.apply {
            writeStartObject()
            writeFieldName("field")

            // Should not be able to write end array after fieldname
            shouldThrow<IllegalJsonOperation> {
                writeEndArray()
            }

            // Should not be able to write end object after fieldname
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
