package maryk.core.json.yaml

import maryk.core.json.ValueType
import maryk.core.json.assertEndDocument
import maryk.core.json.assertEndObject
import maryk.core.json.assertFieldName
import maryk.core.json.assertInvalidYaml
import maryk.core.json.assertStartDocument
import maryk.core.json.assertStartObject
import maryk.core.json.assertValue
import kotlin.test.Test

class DocumentReaderTest {
    @Test
    fun readDocument() {
        createYamlReader("""
        |%YAML 1.2
        |# Comment
        |---
        |  Test
        |---
        | Test2
        |---
        |    Hoho
        |...
        """.trimMargin()).apply {
            assertValue("Test")
            assertStartDocument()
            assertValue("Test2")
            assertStartDocument()
            assertValue("Hoho")
            assertEndDocument()
        }
    }

    @Test
    fun read_comment_with_directive() {
        createYamlReader("""
        |%YAML 1.2
        |# Comment
        |---
        """.trimMargin()).apply {
            assertEndDocument()
        }
    }

    @Test
    fun read_multi_line_comment() {
        createYamlReader("""
        |  # Comment
        |  # Line 2
        """.trimMargin()).apply {
            assertEndDocument()
        }
    }

    @Test
    fun fail_on_wrong_indent() {
        createYamlReader("""
        |  k1: 1
        | k2: 2
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("k1")
            assertValue(1, ValueType.Int)
            assertEndObject()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_wrong_with_comment_indent() {
        createYamlReader("""
        |  k1: 1
        |
        |   # ignore
        |
        | k2: 2
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("k1")
            assertValue(1, ValueType.Int)
            assertEndObject()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_wronger_indent() {
        createYamlReader("""
        |  k1: 1
        |k2: 2
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("k1")
            assertValue(1, ValueType.Int)
            assertEndObject()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_wrong_array_indent() {
        createYamlReader("""
        |  k1: 1
        |--
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("k1")
            assertValue(1.toLong())
            assertEndObject()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_multiple_directives_blocks() {
        createYamlReader("""
        |%YAML 1.2
        |---
        |%YAML 1.2
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_no_close_of_directive_with_document_indicator() {
        createYamlReader("""
        |%YAML 1.2
        |test
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun read_single_dash() {
        createYamlReader("""
        |-
        """.trimMargin()).apply {
            assertValue("-")
        }
    }

    @Test
    fun read_double_dash() {
        createYamlReader("""
        |-- test
        """.trimMargin()).apply {
            assertValue("-- test")
        }
    }

    @Test
    fun read_single_dot_break() {
        createYamlReader("""
        |.
        """.trimMargin()).apply {
            assertValue(".")
        }
    }

    @Test
    fun read_single_dot() {
        createYamlReader("""
        |. test
        """.trimMargin()).apply {
            assertValue(". test")
        }
    }

    @Test
    fun read_double_dot() {
        createYamlReader("""
        |.. dot
        """.trimMargin()).apply {
            assertValue(".. dot")
        }
    }

    @Test
    fun read_double_dot_break() {
        createYamlReader("""
        |..
        """.trimMargin()).apply {
            assertValue("..")
        }
    }

    @Test
    fun read_end_document() {
        createYamlReader("""
        |...
        """.trimMargin()).apply {
            assertEndDocument()
        }
    }
}
