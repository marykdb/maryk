package maryk.yaml

import maryk.json.ValueType
import kotlin.test.Test

class SequenceItemsReaderTest {
    @Test
    fun readSequenceItems() {
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
    fun readSequenceWithComments() {
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
    fun readComplexSequenceItems() {
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
    fun readDoubleSequenceItems() {
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
    fun readWrongSequenceItems() {
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
    fun readWrongSequenceStartItems() {
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
    fun failOnWrongNonSequenceContinuation() {
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
    fun readEmbeddedSequence() {
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
    fun readEmbeddedSequenceInMap() {
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
    fun readMapInsideSequence() {
        createYamlReader("""
         - Number
         - unique: false
           required: false
        """.trimIndent()).apply {
            assertStartArray()
            assertValue("Number")
            assertStartObject()
            assertFieldName("unique")
            assertValue(false, ValueType.Bool)
            assertFieldName("required")
            assertValue(false, ValueType.Bool)
            assertEndObject()
            assertEndArray()
        }
    }

    @Test
    fun readNullValues() {
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
