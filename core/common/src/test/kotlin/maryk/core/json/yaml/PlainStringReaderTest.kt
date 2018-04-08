package maryk.core.json.yaml

import maryk.core.json.assertEndArray
import maryk.core.json.assertEndDocument
import maryk.core.json.assertInvalidYaml
import maryk.core.json.assertStartArray
import maryk.core.json.assertValue
import kotlin.test.Test

class PlainStringReaderTest {
    @Test
    fun read_plain_string() {
        createYamlReader("test").apply {
            assertValue("test")
            assertEndDocument()
        }
    }

    @Test
    fun read_plain_string_with_hash() {
        createYamlReader("test#").apply {
            assertValue("test#")
            assertEndDocument()
        }
    }

    @Test
    fun read_plain_string_with_comment() {
        createYamlReader("test # ignore this").apply {
            assertValue("test")
            assertEndDocument()
        }
    }

    @Test
    fun read_plain_string_with_same_line_breaks() {
        createYamlReader("""
            |  test
            |  test
        """.trimMargin()).apply {
            assertValue("test test")
            assertEndDocument()
        }
    }

    @Test
    fun read_plain_string_with_wrong_line_breaks() {
        createYamlReader("""
            |  test
            | test
        """.trimMargin()).apply {
            assertValue("test")
            assertInvalidYaml()
        }
    }

    @Test
    fun read_plain_string_with_line_breaks() {
        createYamlReader("""
            |  test
            |   test
        """.trimMargin()).apply {
            assertValue("test test")
            assertEndDocument()
        }
    }

    @Test
    fun read_plain_string_with_question_mark() {
        createYamlReader("?test").apply {
            assertValue("?test")
            assertEndDocument()
        }
    }

    @Test
    fun read_plain_string_with_colon() {
        createYamlReader(":test").apply {
            assertValue(":test")
            assertEndDocument()
        }
    }

    @Test
    fun read_plain_string_with_dash() {
        createYamlReader("-test").apply {
            assertValue("-test")
            assertEndDocument()
        }
    }

    @Test
    fun read_plain_string_in_array() {
        createYamlReader("""
            - test1
            - test2
            - -test3
            - :test4
            - ?test5
        """.trimIndent()).apply {
            assertStartArray()
            assertValue("test1")
            assertValue("test2")
            assertValue("-test3")
            assertValue(":test4")
            assertValue("?test5")
            assertEndArray()
            assertEndDocument()
        }
    }
}
