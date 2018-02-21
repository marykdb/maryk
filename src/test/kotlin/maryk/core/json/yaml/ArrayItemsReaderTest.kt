package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForArrayValue
import maryk.core.json.testForEndJson
import maryk.core.json.testForInvalidJson
import kotlin.test.Test

class ArrayItemsReaderTest {
    @Test
    fun read_array_items() {
        val reader = createYamlReader("""
            |     - 'test'
            |     - hey
            |     - "another one"
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayValue(reader, "test")
        testForArrayValue(reader, "hey")
        testForArrayValue(reader, "another one")
        testForArrayEnd(reader)
        testForEndJson(reader)
    }

    @Test
    fun read_array_with_comments() {
        val reader = createYamlReader("""
            |     - 'test' #ignore
            |     - #ignore
            |      hey
            |     - "another one"
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayValue(reader, "test")
        testForArrayValue(reader, "hey")
        testForArrayValue(reader, "another one")
        testForArrayEnd(reader)
        testForEndJson(reader)
    }

    @Test
    fun read_complex_array_items() {
        val reader = createYamlReader("""
            |     - 'test'
            |     - 'hey'
            |     - "another one"
            |          - "deeper"
            |              - 'hey'
            |          - 'and deeper'
            |              - 'hey2'
            |     - "and back again"
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayValue(reader, "test")
        testForArrayValue(reader, "hey")
        testForArrayValue(reader, "another one")
        testForArrayStart(reader)
        testForArrayValue(reader, "deeper")
        testForArrayStart(reader)
        testForArrayValue(reader, "hey")
        testForArrayEnd(reader)
        testForArrayValue(reader, "and deeper")
        testForArrayStart(reader)
        testForArrayValue(reader, "hey2")
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForArrayValue(reader, "and back again")
        testForArrayEnd(reader)
        testForEndJson(reader)
    }

    @Test
    fun read_double_array_items() {
        val reader = createYamlReader("""
            |     -   - 'test'
            |         - 'hey'
            |     - "another one"
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForArrayValue(reader, "test")
        testForArrayValue(reader, "hey")
        testForArrayEnd(reader)
        testForArrayValue(reader, "another one")
        testForArrayEnd(reader)
        testForEndJson(reader)
    }

    @Test
    fun read_wrong_array_items() {
        val reader = createYamlReader("""
            |     - 'test'
            |     "wrong"
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayValue(reader, "test")
        testForInvalidJson(reader)
    }

    @Test
    fun read_wrong_array_start_items() {
        val reader = createYamlReader("""
            |     - 'test'
            |  - 'hey'
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayValue(reader, "test")
        testForArrayEnd(reader)
        testForInvalidJson(reader)
    }
}