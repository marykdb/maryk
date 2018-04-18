package maryk.yaml

import maryk.json.ValueType
import kotlin.test.Test

class SequenceItemsReaderTest {
    @Test
    fun read_sequence_items() {
        createYamlReader("""
            |     - 'test'
            |     - hey
            |     - "another one"
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("test")
            assertValue("hey")
            assertValue("another one")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_with_comments() {
        createYamlReader("""
            |     - 'test' #ignore
            |  # ignore too
            |     - #ignore
            |      hey
            |     - "another one"
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("test")
            assertValue("hey")
            assertValue("another one")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_complex_sequence_items() {
        createYamlReader("""
            |     - 'test'
            |     - 'hey'
            |     - "another one"
            |          - "deeper"
            |              - 'hey'
            |          - 'and deeper'
            |              - 'hey2'
            |     - "and back again"
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("test")
            assertValue("hey")
            assertValue("another one")
            assertStartArray()
            assertValue("deeper")
            assertStartArray()
            assertValue("hey")
            assertEndArray()
            assertValue("and deeper")
            assertStartArray()
            assertValue("hey2")
            assertEndArray()
            assertEndArray()
            assertValue("and back again")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_double_sequence_items() {
        createYamlReader("""
            |     -   - 'test'
            |         - 'hey'
            |     - "another one"
        """.trimMargin()).apply {
            assertStartArray()
            assertStartArray()
            assertValue("test")
            assertValue("hey")
            assertEndArray()
            assertValue("another one")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_wrong_sequence_items() {
        createYamlReader("""
            |     - 'test'
            |     "wrong"
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("test")
            assertInvalidYaml()
        }
    }

    @Test
    fun read_wrong_sequence_start_items() {
        createYamlReader("""
            |     - 'test'
            |  - 'hey'
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("test")
            assertEndArray()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_wrong_non_sequence_continuation() {
        createYamlReader("""
            | - 'test'
            | -wrong
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("test")
            assertInvalidYaml()
        }
    }

    @Test
    fun read_embedded_sequence() {
        createYamlReader("""
        |-
        |  - Reference
        |  - uint
        |-
        |  - Reference
        |  - bool
        """.trimMargin()).apply {
            assertStartArray()
            assertStartArray()
            assertValue("Reference")
            assertValue("uint")
            assertEndArray()
            assertStartArray()
            assertValue("Reference")
            assertValue("bool")
            assertEndArray()
            assertEndArray()
        }
    }

    @Test
    fun read_embedded_sequence_in_map() {
        createYamlReader("""
        |key:
        |-
        |  - Reference
        |  - uint
        |-
        |  - Reference
        |  - bool
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key")
            assertStartArray()
            assertStartArray()
            assertValue("Reference")
            assertValue("uint")
            assertEndArray()
            assertStartArray()
            assertValue("Reference")
            assertValue("bool")
            assertEndArray()
            assertEndArray()
            assertEndObject()
        }
    }

    @Test
    fun read_map_inside_sequence() {
        createYamlReader("""
        | - Number
        | - indexed: false
        |   searchable: true
        |   required: false
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("Number")
            assertStartObject()
            assertFieldName("indexed")
            assertValue(false, ValueType.Bool)
            assertFieldName("searchable")
            assertValue(true, ValueType.Bool)
            assertFieldName("required")
            assertValue(false, ValueType.Bool)
            assertEndObject()
            assertEndArray()
        }
    }

    @Test
    fun read_null_values() {
        createYamlReader("""
        |    - !!null
        |    - a
        |    - ~
        |    - b
        |    - null
        |    - c
        |    - !!null null
        |    - d
        |    -
        """.trimMargin()).apply {
            assertStartArray()
            assertValue(null, ValueType.Null)
            assertValue("a")
            assertValue(null, ValueType.Null)
            assertValue("b")
            assertValue(null, ValueType.Null)
            assertValue("c")
            assertValue(null, ValueType.Null)
            assertValue("d")
            assertValue(null, ValueType.Null)
            assertEndArray()
        }
    }
}
