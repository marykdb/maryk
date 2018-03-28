package maryk.core.json.yaml

import maryk.core.json.assertEndArray
import maryk.core.json.assertInvalidYaml
import maryk.core.json.assertStartArray
import maryk.core.json.assertValue
import kotlin.test.Test

class LiteralStringReaderTest {
    @Test
    fun fail_on_literal_string_without_break() {
        createYamlReader("  | test").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_invalid_indent() {
        createYamlReader("""
            |   |
            | test
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun read_literal_string() {
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
    fun read_literal_string_in_array() {
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
    fun fail_on_invalid_preset_indent() {
        createYamlReader("""
            | |3
            |  test
        """.trimMargin()).apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun read_with_preset_indent() {
        createYamlReader("""
            | |7
            |         test
        """.trimMargin()).apply {
            assertValue("  test\n")
        }
    }

    @Test
    fun read_with_strip_chomp_indent() {
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
    fun read_with_keep_chomp_indent() {
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
    fun read_with_clip_chomp_indent() {
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
    fun read_with_array_with_multiple_chomps_indent() {
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