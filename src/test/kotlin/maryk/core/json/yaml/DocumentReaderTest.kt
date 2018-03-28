package maryk.core.json.yaml

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
}