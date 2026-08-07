package maryk.yaml

import maryk.json.IllegalJsonOperation
import maryk.json.ValueType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class YamlWriterTest {
    @Test
    fun writeExpectedYAML() {
        val output = buildString {
            YamlWriter {
                append(it)
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
        val output = buildString {
            YamlWriter {
                append(it)
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
        val output = buildString {
            YamlWriter {
                append(it)
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
        val output = buildString {
            YamlWriter {
                append(it)
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
        }

        assertEquals(
            """
            {t1: {c1: v1, c2: v2}, t2: {c3: v3}}

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeMultilineStringInObject() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartObject()
                writeFieldName("note")
                writeValue(
                    "Line one\nLine two\nLine three"
                )
                writeEndObject()
            }
        }

        assertEquals(
            """
            note: |-
              Line one
              Line two
              Line three

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeMultilineStringInArray() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartArray()
                writeValue(
                    "Line one\nLine two"
                )
                writeEndArray()
            }
        }

        assertEquals(
            """
            - |-
              Line one
              Line two

            """.trimIndent(),
            output
        )
    }

    @Test
    fun preserveMultilineTrailingNewlinesInObjectFields() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartObject()
                writeFieldName("strip")
                writeValue("Line one\nLine two")
                writeFieldName("clip")
                writeValue("Line one\nLine two\n")
                writeFieldName("keep")
                writeValue("Line one\nLine two\n\n\n")
                writeFieldName("after")
                writeValue("preserved")
                writeEndObject()
            }
        }

        assertEquals(
            "strip: |-\n  Line one\n  Line two\nclip: |\n  Line one\n  Line two\n  \nkeep: |+\n  Line one\n  Line two\n  \n  \nafter: preserved\n",
            output
        )

        createYamlReader(output).apply {
            assertStartObject()
            assertFieldName("strip")
            assertValue("Line one\nLine two")
            assertFieldName("clip")
            assertValue("Line one\nLine two\n")
            assertFieldName("keep")
            assertValue("Line one\nLine two\n\n\n")
            assertFieldName("after")
            assertValue("preserved")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun preserveMultilineTrailingNewlinesInArray() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartArray()
                writeValue("Line one\nLine two")
                writeValue("Line one\nLine two\n")
                writeValue("Line one\nLine two\n\n\n")
                writeValue("preserved")
                writeEndArray()
            }
        }

        assertEquals(
            "- |-\n  Line one\n  Line two\n- |\n  Line one\n  Line two\n  \n- |+\n  Line one\n  Line two\n  \n  \n- preserved\n",
            output
        )

        createYamlReader(output).apply {
            assertStartArray()
            assertValue("Line one\nLine two")
            assertValue("Line one\nLine two\n")
            assertValue("Line one\nLine two\n\n\n")
            assertValue("preserved")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun preserveWhitespaceOnlyMultilineObjectFields() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartObject()
                writeFieldName("clip")
                writeString("\n")
                writeFieldName("keep")
                writeString("\n\n")
                writeFieldName("spaces")
                writeString("  \n \n")
                writeFieldName("after")
                writeString("preserved")
                writeEndObject()
            }
        }

        assertEquals(
            "clip: |2\n  \n  \nkeep: |+2\n  \n  \nspaces: |2\n    \n   \n  \nafter: preserved\n",
            output
        )

        createYamlReader(output).apply {
            assertStartObject()
            assertFieldName("clip")
            assertValue("\n")
            assertFieldName("keep")
            assertValue("\n\n")
            assertFieldName("spaces")
            assertValue("  \n \n")
            assertFieldName("after")
            assertValue("preserved")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun preserveWhitespaceOnlyMultilineArrayItems() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartArray()
                writeString("\n")
                writeString("\n\n")
                writeString("  \n \n")
                writeString("preserved")
                writeEndArray()
            }
        }

        assertEquals(
            "- |2\n  \n  \n- |+2\n  \n  \n- |2\n    \n   \n  \n- preserved\n",
            output
        )

        createYamlReader(output).apply {
            assertStartArray()
            assertValue("\n")
            assertValue("\n\n")
            assertValue("  \n \n")
            assertValue("preserved")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun preserveWhitespaceOnlyLinesInMultilineObjectFields() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartObject()
                writeFieldName("internal")
                writeString("a\n  \nb")
                writeFieldName("leading")
                writeString("\n  \ntext")
                writeFieldName("trailing")
                writeString("a\n \n")
                writeFieldName("after")
                writeString("preserved")
                writeEndObject()
            }
        }

        assertEquals(
            "internal: |-\n  a\n    \n  b\nleading: |-\n  \n    \n  text\ntrailing: |\n  a\n   \n  \nafter: preserved\n",
            output
        )

        createYamlReader(output).apply {
            assertStartObject()
            assertFieldName("internal")
            assertValue("a\n  \nb")
            assertFieldName("leading")
            assertValue("\n  \ntext")
            assertFieldName("trailing")
            assertValue("a\n \n")
            assertFieldName("after")
            assertValue("preserved")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun preserveWhitespaceOnlyLinesInMultilineArrayItems() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartArray()
                writeString("a\n  \nb")
                writeString("\n  \ntext")
                writeString("a\n \n")
                writeString("preserved")
                writeEndArray()
            }
        }

        assertEquals(
            "- |-\n  a\n    \n  b\n- |-\n  \n    \n  text\n- |\n  a\n   \n  \n- preserved\n",
            output
        )

        createYamlReader(output).apply {
            assertStartArray()
            assertValue("a\n  \nb")
            assertValue("\n  \ntext")
            assertValue("a\n \n")
            assertValue("preserved")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun preserveLeadingAndInternalBlankLinesInNestedObject() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartObject()
                writeFieldName("nested")
                writeStartObject()
                writeFieldName("note")
                writeString("\nLine one\n\nLine two")
                writeFieldName("after")
                writeString("preserved")
                writeEndObject()
                writeFieldName("rootAfter")
                writeString("preserved")
                writeEndObject()
            }
        }

        assertEquals(
            "nested:\n  note: |-\n    \n    Line one\n    \n    Line two\n  after: preserved\nrootAfter: preserved\n",
            output
        )

        createYamlReader(output).apply {
            assertStartObject()
            assertFieldName("nested")
            assertStartObject()
            assertFieldName("note")
            assertValue("\nLine one\n\nLine two")
            assertFieldName("after")
            assertValue("preserved")
            assertEndObject()
            assertFieldName("rootAfter")
            assertValue("preserved")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun preserveTaggedMultilineValuesInObjectsAndArrays() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartObject()
                writeFieldName("tagged")
                writeTag("!!str")
                writeString("Object\nvalue\n")
                writeFieldName("values")
                writeStartArray()
                writeTag("!!str")
                writeString("Array\nvalue\n\n")
                writeString("preserved")
                writeEndArray()
                writeEndObject()
            }
        }

        assertEquals(
            "tagged: !!str |\n  Object\n  value\n  \nvalues:\n- !!str |+\n  Array\n  value\n  \n- preserved\n",
            output
        )

        createYamlReader(output).apply {
            assertStartObject()
            assertFieldName("tagged")
            assertValue("Object\nvalue\n", ValueType.String)
            assertFieldName("values")
            assertStartArray()
            assertValue("Array\nvalue\n\n", ValueType.String)
            assertValue("preserved")
            assertEndArray()
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun quoteAliasLikeStringValues() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartObject()
                writeFieldName("ref")
                writeString("*alias")
                writeEndObject()
            }
        }

        assertEquals(
            """
            ref: '*alias'

            """.trimIndent(),
            output
        )

        createYamlReader(output).apply {
            assertStartObject()
            assertFieldName("ref")
            assertValue("*alias")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun quoteFlowDelimitersInCompactMapValues() {
        listOf('[', ']', '{', '}', ',').forEach { delimiter ->
            val value = "value$delimiter"
            val output = buildString {
                YamlWriter { append(it) }.apply {
                    writeStartObject(true)
                    writeFieldName("value")
                    writeString(value)
                    writeEndObject()
                }
            }

            assertEquals("{value: '$value'}\n", output)

            createYamlReader(output).apply {
                assertStartObject()
                assertFieldName("value")
                assertValue(value)
                assertEndObject()
                assertEndDocument()
            }
        }
    }

    @Test
    fun quoteFlowDelimitersInCompactSequenceValues() {
        listOf('[', ']', '{', '}', ',').forEach { delimiter ->
            val value = "value$delimiter"
            val output = buildString {
                YamlWriter { append(it) }.apply {
                    writeStartArray(true)
                    writeString(value)
                    writeEndArray()
                }
            }

            assertEquals("['$value']\n", output)

            createYamlReader(output).apply {
                assertStartArray()
                assertValue(value)
                assertEndArray()
                assertEndDocument()
            }
        }
    }

    @Test
    fun quoteFlowDelimitersInCompactMapKeys() {
        listOf('[', ']', '{', '}', ',').forEach { delimiter ->
            val key = "key$delimiter"
            val output = buildString {
                YamlWriter { append(it) }.apply {
                    writeStartObject(true)
                    writeFieldName(key)
                    writeString("value")
                    writeEndObject()
                }
            }

            assertEquals("{'$key': value}\n", output)

            createYamlReader(output).apply {
                assertStartObject()
                assertFieldName(key)
                assertValue("value")
                assertEndObject()
                assertEndDocument()
            }
        }
    }

    @Test
    fun quoteFlowMapKeysWithEmbeddedQuotes() {
        val key = "key, it's]"
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartObject(true)
                writeFieldName(key)
                writeString("value")
                writeEndObject()
            }
        }

        assertEquals("{'key, it''s]': value}\n", output)

        createYamlReader(output).apply {
            assertStartObject()
            assertFieldName(key)
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun quoteQuestionMarkInCompactMapKey() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartObject(true)
                writeFieldName("?")
                writeString("value")
                writeEndObject()
            }
        }

        assertEquals("{'?': value}\n", output)

        createYamlReader(output).apply {
            assertStartObject()
            assertFieldName("?")
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun quoteQuestionMarkInBlockMapKey() {
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartObject()
                writeFieldName("normal")
                writeString("first")
                writeFieldName("?")
                writeString("value")
                writeEndObject()
            }
        }

        assertEquals("normal: first\n'?': value\n", output)

        createYamlReader(output).apply {
            assertStartObject()
            assertFieldName("normal")
            assertValue("first")
            assertFieldName("?")
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun keepNumericAndTimeMapKeysUnquoted() {
        val output = buildString {
            YamlWriter(::append).apply {
                writeStartObject()
                writeFieldName("12")
                writeString("numeric")
                writeFieldName("12:55")
                writeString("time")
                writeEndObject()
            }
        }

        assertEquals("12: numeric\n12:55: time\n", output)
        createYamlReader(output).apply {
            assertStartObject()
            assertFieldName("12")
            assertValue("numeric")
            assertFieldName("12:55")
            assertValue("time")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun quoteFlowDelimitersAndEmbeddedQuotesRoundTrip() {
        val value = "value, it's]"
        val output = buildString {
            YamlWriter { append(it) }.apply {
                writeStartObject(true)
                writeFieldName("value")
                writeString(value)
                writeEndObject()
            }
        }

        assertEquals("{value: 'value, it''s]'}\n", output)

        createYamlReader(output).apply {
            assertStartObject()
            assertFieldName("value")
            assertValue(value)
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun writeYamlInDoubleArray() {
        val output = buildString {
            YamlWriter {
                append(it)
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
    fun writeEmptyObjectInNestedArrays() {
        val output = buildString {
            YamlWriter(::append).apply {
                writeStartArray()
                writeStartArray()
                writeStartObject()
                writeEndObject()
                writeEndArray()
                writeEndArray()
            }
        }

        assertEquals("- - {}\n", output)
    }

    @Test
    fun writeEmptyObjectAtRoot() {
        val output = buildString {
            YamlWriter(::append).apply {
                writeStartObject()
                writeEndObject()
            }
        }

        assertEquals("{}", output)
        createYamlReader(output).apply {
            assertStartObject()
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun writeEmptyObjectInArrayBeforeSibling() {
        val output = buildString {
            YamlWriter(::append).apply {
                writeStartArray()
                writeStartObject()
                writeEndObject()
                writeValue("next")
                writeEndArray()
            }
        }

        assertEquals("- {}\n- next\n", output)
    }

    @Test
    fun writeTaggedEmptyObjectWithoutFlowMap() {
        val output = buildString {
            YamlWriter(::append).apply {
                writeStartObject()
                writeFieldName("map")
                writeTag("!map")
                writeStartObject()
                writeEndObject()
                writeEndObject()
            }
        }

        assertEquals("map: !map\n", output)
    }

    @Test
    fun writeEmptyObjectAsComplexMapKeyAndValue() {
        val output = buildString {
            YamlWriter(::append).apply {
                writeStartObject()
                writeStartComplexField()
                writeStartObject()
                writeEndObject()
                writeEndComplexField()
                writeStartObject()
                writeEndObject()
                writeEndObject()
            }
        }

        assertEquals("? {}\n: {}\n", output)
    }

    @Test
    fun writeEmptyObjectInsideCompactObject() {
        val output = buildString {
            YamlWriter(::append).apply {
                writeStartObject(true)
                writeFieldName("map")
                writeStartObject()
                writeEndObject()
                writeEndObject()
            }
        }

        assertEquals("{map: {}}\n", output)
    }

    @Test
    fun writeYamlIndoubleArrayAndTag() {
        val output = buildString {
            YamlWriter {
                append(it)
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
        val output = buildString {
            YamlWriter {
                append(it)
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
        val output = buildString {
            YamlWriter {
                append(it)
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
        val output = buildString {
            YamlWriter {
                append(it)
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
        val output = buildString {
            YamlWriter {
                append(it)
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
        val output = buildString {
            YamlWriter {
                append(it)
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
        buildString {
            YamlWriter {
                append(it)
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
    }

    @Test
    fun notAllowIllegalOperationsInsideAnArray() {
        buildString {
            YamlWriter {
                append(it)
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
    }

    @Test
    fun notAllowIllegalOperationsWithinAnObject() {
        buildString {
            YamlWriter {
                append(it)
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
    }

    @Test
    fun notAllowIllegalOperationsAfterAnObjectFieldName() {
        buildString {
            YamlWriter {
                append(it)
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
}
