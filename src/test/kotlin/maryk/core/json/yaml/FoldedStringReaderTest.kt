package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForArrayValue
import maryk.core.json.testForInvalidYaml
import maryk.core.json.testForObjectValue
import kotlin.test.Test

class FoldedStringReaderTest {
    @Test
    fun fail_on_folded_string_without_break() {
        val reader = createYamlReader("  > test")
        testForInvalidYaml(reader)
    }

    @Test
    fun fail_on_invalid_indent() {
        val reader = createYamlReader("""
            |   >
            | test
        """.trimMargin())
        testForInvalidYaml(reader)
    }

    @Test
    fun fail_on_invalid_preset_indent() {
        val reader = createYamlReader("""
            | >3
            |  test
        """.trimMargin())
        testForInvalidYaml(reader)
    }

    @Test
    fun read_with_preset_indent() {
        val reader = createYamlReader("""
            | >7
            |         test
        """.trimMargin())
        testForObjectValue(reader, "  test\n")
    }

    @Test
    fun read_folded_string() {
        val reader = createYamlReader("""
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
        """.trimMargin())
        testForObjectValue(reader, "\nfolded line\nnext line\n  * bullet\n\n  * list\n  * lines\n\nlast line\n");
    }

    @Test
    fun read_folded_string_in_array() {
        val reader = createYamlReader("""
            |- >
            |  test
            |- >
            |  test2
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayValue(reader, "test\n")
        testForArrayValue(reader, "test2\n")
        testForArrayEnd(reader)
    }

    @Test
    fun fail_on_double_chomp() {
        val reader = createYamlReader("""
            |>-2+
            |  test
        """.trimMargin())
        testForInvalidYaml(reader)
    }

    @Test
    fun fail_on_double_indent() {
        val reader = createYamlReader("""
            |>2-5
            |  test
        """.trimMargin())
        testForInvalidYaml(reader)
    }


    @Test
    fun read_with_strip_chomp_indent() {
        val reader = createYamlReader("""
            | |-
            | test
            |
            |
        """.trimMargin())
        testForObjectValue(reader, "test")
    }

    @Test
    fun read_with_keep_chomp_indent() {
        val reader = createYamlReader("""
            | >+
            | test
            |
            |
        """.trimMargin())
        testForObjectValue(reader, "test\n\n")
    }

    @Test
    fun read_with_clip_chomp_indent() {
        val reader = createYamlReader("""
            | >
            | test
            |
            |
        """.trimMargin())
        testForObjectValue(reader, "test\n")
    }

    @Test
    fun read_with_array_with_multiple_chomps_indent() {
        val reader = createYamlReader("""
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
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayValue(reader, "test1\n")
        testForArrayValue(reader, "test2\n\n\n")
        testForArrayValue(reader, "test3")
        testForArrayValue(reader, "test4")
        testForArrayEnd(reader)
    }
}