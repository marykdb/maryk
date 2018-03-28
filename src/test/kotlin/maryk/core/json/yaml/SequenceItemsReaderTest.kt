package maryk.core.json.yaml

import maryk.core.json.assertEndArray
import maryk.core.json.assertEndDocument
import maryk.core.json.assertEndObject
import maryk.core.json.assertFieldName
import maryk.core.json.assertInvalidYaml
import maryk.core.json.assertStartArray
import maryk.core.json.assertStartObject
import maryk.core.json.assertValue
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
}
