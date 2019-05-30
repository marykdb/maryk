package maryk.yaml

import maryk.json.IllegalJsonOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class YamlWriterTest {
    @Test
    fun writeExpectedYAML() {
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

        assertEquals(
            """
            - 1
            - '#Test''s'
            - 3.5
            - true
            - test: false
              test2: value
            - another: yes

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeYamlInMapEmbeddedInMap() {
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

        assertEquals(
            """
            t1:
              c1: v1
              c2: v2
            t2:
              c3: v3

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeYamlInmapWithSimpleChildMap() {
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

        assertEquals(
            """
            t1: {c1: v1, c2: v2}
            t2: {c3: v3}

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeYamlInSimpleEmbeddedMaps() {
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

        assertEquals(
            """
            {t1: {c1: v1, c2: v2}, t2: {c3: v3}}

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeYamlInDoubleArray() {
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

        assertEquals(
            """
            - - 1
              - 2
            - - 3
              - 4

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeYamlIndoubleArrayAndTag() {
        var output = ""
        YamlWriter {
            output += it
        }.apply {
            writeStartArray()
            writeTag("!tag")
            writeStartArray()
            writeValue("1")
            writeValue("2")
            writeEndArray()
            writeTag("!tag")
            writeStartArray()
            writeValue("3")
            writeValue("4")
            writeEndArray()
            writeEndArray()
        }

        assertEquals(
            """
            - !tag
              - 1
              - 2
            - !tag
              - 3
              - 4

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeYamlIndoubleArrayWithLessComplexChild() {
        var output = ""
        YamlWriter {
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

        assertEquals(
            """
            - [1, 2]
            - [3, 4]

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeYamlInlessComplexDoubleArray() {
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

        assertEquals(
            """
            [[1, 2],[3, 4]]

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeYAMLWithTags() {
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

        assertEquals(
            """
            !!omap
            t1: !!str true
            t2: !!set
            - !!int 30
            t3: !!omap
              a1: 1
            t4: !!omap {a1: !!int 1}
            t5: !!set [!!int 30]

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeYAMLWithTagsInArray() {
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

        assertEquals(
            """
            key:
            - !Foo
              k1: 30
            - !Bar
              k2: 40

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeYAMLWithComplexFields() {
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

        assertEquals(
            """
            - ? - a1
                - a2
              : value 1
              ? f1: v1
                f2: v2
              : !tag value 2
              ? {f1: v1, f2: v2}
              :
              - a1
              - a2
              ? [a1, a2]
              : f1: v1
                f2: v2

            """.trimIndent(),
            output
        )
    }

    @Test
    fun notStartWithUnallowedYAMLTypes() {
        var output = ""

        YamlWriter {
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
        YamlWriter {
            output += it
        }.apply {
            writeStartArray()

            // Should not be able to write end object after start array
            assertFailsWith<IllegalJsonOperation> {
                writeEndObject()
            }

            // Should not be able to write fieldname to array
            assertFailsWith<IllegalJsonOperation> {
                writeFieldName("test")
            }
        }
    }


    @Test
    fun notAllowIllegalOperationsWithinAnObject() {
        var output = ""

        YamlWriter {
            output += it
        }.apply {
            writeStartObject()

            // Should not be able to write end array after start object
            assertFailsWith<IllegalJsonOperation> {
                writeEndArray()
            }

            // Should not be able to write value before a fieldname
            assertFailsWith<IllegalJsonOperation> {
                writeValue("false")
            }

            // Should not be able to write string value before a fieldname
            assertFailsWith<IllegalJsonOperation> {
                writeString("test")
            }
        }
    }

    @Test
    fun notAllowIllegalOperationsAfterAnObjectFieldName() {
        var output = ""
        YamlWriter {
            output += it
        }.apply {
            writeStartObject()
            writeFieldName("field")

            // Should not be able to write end array after fieldname
            assertFailsWith<IllegalJsonOperation> {
                writeEndArray()
            }

            // Should not be able to write end object after fieldname
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
