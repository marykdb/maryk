package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForInvalidYaml
import maryk.core.json.testForValue
import kotlin.test.Test

class LiteralStringReaderTest {
    @Test
    fun fail_on_literal_string_without_break() {
        val reader = createYamlReader("  | test")
        testForInvalidYaml(reader)
    }

    @Test
    fun fail_on_invalid_indent() {
        val reader = createYamlReader("""
            |   |
            | test
        """.trimMargin())
        testForInvalidYaml(reader)
    }

    @Test
    fun read_literal_string() {
        val reader = createYamlReader("""
            ||
            |
            | test
            | another
            |  line
            |
            |  - haha
        """.trimMargin())
        testForValue(reader, "\ntest\nanother\n line\n\n - haha\n")
    }

    @Test
    fun read_literal_string_in_array() {
        val reader = createYamlReader("""
            |- |
            |  test
            |- |
            |  test2
        """.trimMargin())
        testForArrayStart(reader)
        testForValue(reader, "test\n")
        testForValue(reader, "test2\n")
        testForArrayEnd(reader)
    }


    @Test
    fun fail_on_invalid_preset_indent() {
        val reader = createYamlReader("""
            | |3
            |  test
        """.trimMargin())
        testForInvalidYaml(reader)
    }

    @Test
    fun read_with_preset_indent() {
        val reader = createYamlReader("""
            | |7
            |         test
        """.trimMargin())
        testForValue(reader, "  test\n")
    }

    @Test
    fun read_with_strip_chomp_indent() {
        val reader = createYamlReader("""
            | |-
            | test
            |
            |
        """.trimMargin())
        testForValue(reader, "test")
    }


    @Test
    fun read_with_keep_chomp_indent() {
        val reader = createYamlReader("""
            | |+
            | test
            |
            |
        """.trimMargin())
        testForValue(reader, "test\n\n")
    }

    @Test
    fun read_with_clip_chomp_indent() {
        val reader = createYamlReader("""
            | |
            | test
            |
            |
        """.trimMargin())
        testForValue(reader, "test\n")
    }

    @Test
    fun read_with_array_with_multiple_chomps_indent() {
        val reader = createYamlReader("""
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
        """.trimMargin())
        testForArrayStart(reader)
        testForValue(reader, "test1\n")
        testForValue(reader, "test2\n\n\n")
        testForValue(reader, "test3")
        testForValue(reader, "test4")
        testForArrayEnd(reader)
    }
}