package maryk.yaml

import maryk.json.ValueType
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
    fun readCommentWithDirective() {
        createYamlReader("""
        |%YAML 1.2
        |# Comment
        |---
        """.trimMargin()).apply {
            assertEndDocument()
        }
    }

    @Test
    fun readMultiLineComment() {
        createYamlReader("""
        |  # Comment
        |  # Line 2
        """.trimMargin()).apply {
            assertEndDocument()
        }
    }

    @Test
    fun failOnWrongIndent() {
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
    fun failOnWrongWithCommentIndent() {
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
    fun failOnWrongerIndent() {
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
    fun failOnWrongArrayIndent() {
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
    fun failOnMultipleDirectivesBlocks() {
        createYamlReader("""
        |%YAML 1.2
        |---
        |%YAML 1.2
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnNoCloseOfDirectiveWithDocumentIndicator() {
        createYamlReader("""
        |%YAML 1.2
        |test
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun readSingleDash() {
        createYamlReader("""
        |-
        """.trimMargin()).apply {
            assertValue("-")
        }
    }

    @Test
    fun readDoubleDash() {
        createYamlReader("""
        |-- test
        """.trimMargin()).apply {
            assertValue("-- test")
        }
    }

    @Test
    fun readSingleDotBreak() {
        createYamlReader("""
        |.
        """.trimMargin()).apply {
            assertValue(".")
        }
    }

    @Test
    fun readSingleDot() {
        createYamlReader("""
        |. test
        """.trimMargin()).apply {
            assertValue(". test")
        }
    }

    @Test
    fun readDoubleDot() {
        createYamlReader("""
        |.. dot
        """.trimMargin()).apply {
            assertValue(".. dot")
        }
    }

    @Test
    fun readDoubleDotBreak() {
        createYamlReader("""
        |..
        """.trimMargin()).apply {
            assertValue("..")
        }
    }

    @Test
    fun readEndDocument() {
        createYamlReader("""
        |...
        """.trimMargin()).apply {
            assertEndDocument()
        }
    }
}
