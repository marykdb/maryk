package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForArrayValue
import maryk.core.json.testForInvalidJson
import maryk.core.json.testForObjectValue
import kotlin.test.Test

class LiteralStringReaderTest {
    @Test
    fun fail_on_literal_string_without_break() {
        val reader = createYamlReader("  | test")
        testForInvalidJson(reader)
    }

    @Test
    fun fail_on_invalid_indent() {
        val reader = createYamlReader("""
            |   |
            | test
        """.trimMargin())
        testForInvalidJson(reader)
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
        testForObjectValue(reader, "\ntest\nanother\n line\n\n - haha\n")
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
        testForArrayValue(reader, "test\n")
        testForArrayValue(reader, "test2\n")
        testForArrayEnd(reader)
    }
}