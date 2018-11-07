package maryk.yaml

import kotlin.test.Test

class LiteralStringReaderTest {
    @Test
    fun failOnLiteralStringWithoutBreak() {
        createYamlReader("  | test").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnInvalidIndent() {
        createYamlReader("""
            |   |
            | test
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun readLiteralString() {
        createYamlReader("""
            ||
            |
            | test
            | another
            |  line
            |
            |  - haha
        """.trimMargin()).apply {
            assertValue("\ntest\nanother\n line\n\n - haha\n")
        }
    }

    @Test
    fun readLiteralStringInArray() {
        createYamlReader("""
            |- |
            |  test
            |- |
            |  test2
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("test\n")
            assertValue("test2\n")
            assertEndArray()
        }
    }


    @Test
    fun failOnInvalidPresetIndent() {
        createYamlReader("""
            | |3
            |  test
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun readWithPresetIndent() {
        createYamlReader("""
            | |7
            |         test
        """.trimMargin()).apply {
            assertValue("  test\n")
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
            | |+
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
            | |
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
            | - |
            |   test1
            |
            |
            | - |+
            |   test2
            |
            |
            |#ignore
            | - |-
            |   test3
            |
            |
            | - |-2
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
