package maryk.yaml

import kotlin.test.Test

class FoldedStringReaderTest {
    @Test
    fun failOnFoldedStringWithoutBreak() {
        createYamlReader("  > test").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnInvalidIndent() {
        createYamlReader("""
            |   >
            | test
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnInvalidPresetIndent() {
        createYamlReader("""
            | >3
            |  test
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun readWithPresetIndent() {
        createYamlReader("""
            | >7
            |         test
        """.trimMargin()).apply {
            assertValue("  test\n")
        }
    }

    @Test
    fun readFoldedString() {
        createYamlReader("""
        |>
        |
        | folded
        | line
        |
        | next
        | line
        |   * bullet
        |
        |   * list
        |   * lines
        |
        | last
        | line
        """.trimMargin()).apply {
            assertValue("\nfolded line\nnext line\n  * bullet\n\n  * list\n  * lines\n\nlast line\n")
        }
    }

    @Test
    fun readFoldedStringInArray() {
        createYamlReader("""
            |- >
            |  test
            |- >
            |  test2
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("test\n")
            assertValue("test2\n")
            assertEndArray()
        }
    }

    @Test
    fun failOnDoubleChomp() {
        createYamlReader("""
            |>-2+
            |  test
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnDoubleIndent() {
        createYamlReader("""
            |>2-5
            |  test
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun readWithStripChompIndent() {
        createYamlReader("""
            | |-
            | test
            |
            |
        """.trimMargin()).apply {
            assertValue("test")
        }
    }

    @Test
    fun readWithKeepChompIndent() {
        createYamlReader("""
            | >+
            | test
            |
            |
        """.trimMargin()).apply {
            assertValue("test\n\n")
        }
    }

    @Test
    fun readWithClipChompIndent() {
        createYamlReader("""
            | >
            | test
            |
            |
        """.trimMargin()).apply {
            assertValue("test\n")
        }
    }

    @Test
    fun readWithArrayWithMultipleChompsIndent() {
        createYamlReader("""
            | - >
            |   test1
            |
            |
            | - >+
            |   test2
            |
            |
            | - >-
            |   test3
            |
            |
            | - >-2
            |   test4
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("test1\n")
            assertValue("test2\n\n\n")
            assertValue("test3")
            assertValue("test4")
            assertEndArray()
        }
    }
}
