package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForArrayValue
import maryk.core.json.testForEndJson
import maryk.core.json.testForObjectValue
import kotlin.test.Test

class PlainStringReaderTest {
    @Test
    fun read_plain_string() {
        val reader = createYamlReader("test")
        testForObjectValue(reader, "test")
        testForEndJson(reader)
    }

    @Test
    fun read_plain_string_with_question_mark() {
        val reader = createYamlReader("?test")
        testForObjectValue(reader, "?test")
        testForEndJson(reader)
    }

    @Test
    fun read_plain_string_with_colon() {
        val reader = createYamlReader(":test")
        testForObjectValue(reader, ":test")
        testForEndJson(reader)
    }

    @Test
    fun read_plain_string_with_dash() {
        val reader = createYamlReader("-test")
        testForObjectValue(reader, "-test")
        testForEndJson(reader)
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
        testForArrayValue(reader, "test1")
        testForArrayValue(reader, "test2")
        testForArrayValue(reader, "-test3")
        testForArrayValue(reader, ":test4")
        testForArrayValue(reader, "?test5")
        testForArrayEnd(reader)
        testForEndJson(reader)
    }
}