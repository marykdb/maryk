package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForInvalidJson
import maryk.core.json.testForValue
import kotlin.test.Test

class FlowSequenceReaderTest {
    @Test
    fun read_array_items() {
        val reader = createYamlReader("""
            |     - ["test1", "test2", "test3"]
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "test1")
        testForValue(reader, "test2")
        testForValue(reader, "test3")
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_array_items_plain() {
        val reader = createYamlReader("""
            |     - [test1, test2, test3]
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "test1")
        testForValue(reader, "test2")
        testForValue(reader, "test3")
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_array_items_plain_multiline() {
        val reader = createYamlReader("""
            |     - [test1
            |      longer
            |      and longer,
            |       test2,
            |       test3]
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "test1 longer and longer")
        testForValue(reader, "test2")
        testForValue(reader, "test3")
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_array_items_plain_wrong_multiline() {
        val reader = createYamlReader("""
            |     - [test1
            |     wrong]
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForInvalidJson(reader)
    }

    @Test
    fun read_array_with_whitespacing_items() {
        val reader = createYamlReader("""
            |     - ["test1"    ,    "test2",
            |"test3"  ]
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "test1")
        testForValue(reader, "test2")
        testForValue(reader, "test3")
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }
}