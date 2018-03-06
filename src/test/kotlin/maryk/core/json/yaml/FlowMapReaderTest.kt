package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForFieldName
import maryk.core.json.testForInvalidJson
import maryk.core.json.testForObjectEnd
import maryk.core.json.testForObjectStart
import maryk.core.json.testForValue
import kotlin.test.Test

class FlowMapReaderTest {
    @Test
    fun read_map_items() {
        val reader = createYamlReader("""
        |     - {"key0","key1": "value1", 'key2': 'value2'}
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "key0")
        testForValue(reader, null)
        testForFieldName(reader, "key1")
        testForValue(reader, "value1")
        testForFieldName(reader, "key2")
        testForValue(reader, "value2")
        testForObjectEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_map_items_plain_string() {
        val reader = createYamlReader("""
        |     - {key0, key1: value1, key2: value2}
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "key0")
        testForValue(reader, null)
        testForFieldName(reader, "key1")
        testForValue(reader, "value1")
        testForFieldName(reader, "key2")
        testForValue(reader, "value2")
        testForObjectEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_map_items_plain_string_multiline() {
        val reader = createYamlReader("""
        |     - {key0,
        |      key1:
        |
        |          value1,
        |      key2:
        |       value2
        |        and longer
        |
        |        and longer
        |       }
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "key0")
        testForValue(reader, null)
        testForFieldName(reader, "key1")
        testForValue(reader, "value1")
        testForFieldName(reader, "key2")
        testForValue(reader, "value2 and longer and longer")
        testForObjectEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_map_items_plain_string_wrong_multiline() {
        val reader = createYamlReader("""
        |     - {key0
        |     multiline
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForInvalidJson(reader)
    }

    @Test
    fun read_map_multiline_items() {
        val reader = createYamlReader("""
        |   {"key0",
        |"key1":
        |"value1",
        |'key2': 'value2'}
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key0")
        testForValue(reader, null)
        testForFieldName(reader, "key1")
        testForValue(reader, "value1")
        testForFieldName(reader, "key2")
        testForValue(reader, "value2")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }
}