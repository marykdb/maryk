package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForInvalidYaml
import maryk.core.json.testForValue
import kotlin.test.Test

class PlainStringReaderTest {
    @Test
    fun read_plain_string() {
        val reader = createYamlReader("test")
        testForValue(reader, "test")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_plain_string_with_hash() {
        val reader = createYamlReader("test#")
        testForValue(reader, "test#")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_plain_string_with_comment() {
        val reader = createYamlReader("test # ignore this")
        testForValue(reader, "test")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_plain_string_with_same_line_breaks() {
        val reader = createYamlReader("""
            |  test
            |  test
        """.trimMargin())
        testForValue(reader, "test test")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_plain_string_with_wrong_line_breaks() {
        val reader = createYamlReader("""
            |  test
            | test
        """.trimMargin())
        testForInvalidYaml(reader)
    }

    @Test
    fun read_plain_string_with_line_breaks() {
        val reader = createYamlReader("""
            |  test
            |   test
        """.trimMargin())
        testForValue(reader, "test test")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_plain_string_with_question_mark() {
        val reader = createYamlReader("?test")
        testForValue(reader, "?test")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_plain_string_with_colon() {
        val reader = createYamlReader(":test")
        testForValue(reader, ":test")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_plain_string_with_dash() {
        val reader = createYamlReader("-test")
        testForValue(reader, "-test")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_plain_string_in_array() {
        val reader = createYamlReader("""
            - test1
            - test2
            - -test3
            - :test4
            - ?test5
        """.trimIndent())
        testForArrayStart(reader)
        testForValue(reader, "test1")
        testForValue(reader, "test2")
        testForValue(reader, "-test3")
        testForValue(reader, ":test4")
        testForValue(reader, "?test5")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }
}