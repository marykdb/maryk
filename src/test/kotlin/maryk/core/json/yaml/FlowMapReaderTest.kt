package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForFieldName
import maryk.core.json.testForInvalidYaml
import maryk.core.json.testForObjectEnd
import maryk.core.json.testForObjectStart
import maryk.core.json.testForValue
import kotlin.test.Test

class FlowMapReaderTest {
    @Test
    fun read_map_items() {
        val reader = createYamlReader("""
        |     - {"key0",key1: "value1", 'key2': 'value2'}
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
    fun read_map_and_sequence_in_map_items() {
        val reader = createYamlReader("""
        |     - {"key0","key1": {e1: v1}, 'key2': [v1, v2]}
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "key0")
        testForValue(reader, null)
        testForFieldName(reader, "key1")
        testForObjectStart(reader)
        testForFieldName(reader, "e1")
        testForValue(reader, "v1")
        testForObjectEnd(reader)
        testForFieldName(reader, "key2")
        testForArrayStart(reader)
        testForValue(reader, "v1")
        testForValue(reader, "v2")
        testForArrayEnd(reader)
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
        testForInvalidYaml(reader)
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

    @Test
    fun read_map_with_explicit_key_items() {
        val reader = createYamlReader("""
        |   {? ,test: v1}
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForValue(reader, null)
        testForFieldName(reader, "test")
        testForValue(reader, "v1")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_map_with_explicit_direct_key_items() {
        val reader = createYamlReader("""
        |   {?,test: v1}
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForValue(reader, null)
        testForFieldName(reader, "test")
        testForValue(reader, "v1")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_map_with_explicit_defined_key_with_value_items() {
        val reader = createYamlReader("""
        |   {? t1: v0,test: v1}
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "t1")
        testForValue(reader, "v0")
        testForFieldName(reader, "test")
        testForValue(reader, "v1")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_map_with_explicit_defined_key_items() {
        val reader = createYamlReader("""
        |   {? t1}
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "t1")
        testForValue(reader, null)
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_map_with_explicit_key_value_items() {
        val reader = createYamlReader("""
        |   {?: v0,test: v1}
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForValue(reader, "v0")
        testForFieldName(reader, "test")
        testForValue(reader, "v1")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }
}