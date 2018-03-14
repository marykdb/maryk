package maryk.core.json.yaml

import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForDocumentStart
import maryk.core.json.testForFieldName
import maryk.core.json.testForInvalidYaml
import maryk.core.json.testForObjectStart
import maryk.core.json.testForValue
import kotlin.test.Test

class DocumentReaderTest {
    @Test
    fun readDocument() {
        val reader = createYamlReader("""
        |%YAML 1.2
        |# Comment
        |---
        |  Test
        |---
        | Test2
        |---
        |    Hoho
        |...
        """.trimMargin())
        testForValue(reader, "Test")
        testForDocumentStart(reader)
        testForValue(reader, "Test2")
        testForDocumentStart(reader)
        testForValue(reader, "Hoho")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_comment_with_directive() {
        val reader = createYamlReader("""
        |%YAML 1.2
        |# Comment
        |---
        """.trimMargin())
        testForDocumentEnd(reader)
    }

    @Test
    fun read_multi_line_comment() {
        val reader = createYamlReader("""
        |  # Comment
        |  # Line 2
        """.trimMargin())
        testForDocumentEnd(reader)
    }

    @Test
    fun fail_on_wrong_indent() {
        val reader = createYamlReader("""
        |  k1: 1
        | k2: 2
        """.trimMargin())

        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForValue(reader, 1.toLong())
        testForInvalidYaml(reader)
    }

    @Test
    fun fail_on_wrong_with_comment_indent() {
        val reader = createYamlReader("""
        |  k1: 1
        |
        |   # ignore
        |
        | k2: 2
        """.trimMargin())

        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForValue(reader, 1.toLong())
        testForInvalidYaml(reader)
    }

    @Test
    fun fail_on_wronger_indent() {
        val reader = createYamlReader("""
        |  k1: 1
        |k2: 2
        """.trimMargin())

        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForValue(reader, 1.toLong())
        testForInvalidYaml(reader)
    }

    @Test
    fun fail_on_wrong_array_indent() {
        val reader = createYamlReader("""
        |  k1: 1
        |--
        """.trimMargin())

        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForValue(reader, 1.toLong())
        testForInvalidYaml(reader)
    }
}