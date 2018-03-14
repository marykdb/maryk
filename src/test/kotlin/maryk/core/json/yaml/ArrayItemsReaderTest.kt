package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForInvalidYaml
import maryk.core.json.testForValue
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
        testForValue(reader, "test")
        testForValue(reader, "hey")
        testForValue(reader, "another one")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_array_with_comments() {
        val reader = createYamlReader("""
            |     - 'test' #ignore
            |  # ignore too
            |     - #ignore
            |      hey
            |     - "another one"
        """.trimMargin())
        testForArrayStart(reader)
        testForValue(reader, "test")
        testForValue(reader, "hey")
        testForValue(reader, "another one")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
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
        testForValue(reader, "test")
        testForValue(reader, "hey")
        testForValue(reader, "another one")
        testForArrayStart(reader)
        testForValue(reader, "deeper")
        testForArrayStart(reader)
        testForValue(reader, "hey")
        testForArrayEnd(reader)
        testForValue(reader, "and deeper")
        testForArrayStart(reader)
        testForValue(reader, "hey2")
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForValue(reader, "and back again")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
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
        testForValue(reader, "test")
        testForValue(reader, "hey")
        testForArrayEnd(reader)
        testForValue(reader, "another one")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_wrong_array_items() {
        val reader = createYamlReader("""
            |     - 'test'
            |     "wrong"
        """.trimMargin())
        testForArrayStart(reader)
        testForValue(reader, "test")
        testForInvalidYaml(reader)
    }

    @Test
    fun read_wrong_array_start_items() {
        val reader = createYamlReader("""
            |     - 'test'
            |  - 'hey'
        """.trimMargin())
        testForArrayStart(reader)
        testForValue(reader, "test")
        testForArrayEnd(reader)
        testForInvalidYaml(reader)
    }
}