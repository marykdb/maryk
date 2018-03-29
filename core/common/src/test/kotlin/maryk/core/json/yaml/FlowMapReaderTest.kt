package maryk.core.json.yaml

import maryk.core.json.ValueType
import maryk.core.json.assertEndArray
import maryk.core.json.assertEndComplexFieldName
import maryk.core.json.assertEndDocument
import maryk.core.json.assertEndObject
import maryk.core.json.assertFieldName
import maryk.core.json.assertInvalidYaml
import maryk.core.json.assertStartArray
import maryk.core.json.assertStartComplexFieldName
import maryk.core.json.assertStartObject
import maryk.core.json.assertValue
import kotlin.test.Test

class FlowMapReaderTest {
    @Test
    fun read_map_items() {
        createYamlReader("""
        |     - {"key0",-key1: "value1", 'key2': 'value2'}
        """.trimMargin()).apply {
            assertStartArray()
            assertStartObject()
            assertFieldName("key0")
            assertValue(null)
            assertFieldName("-key1")
            assertValue("value1")
            assertFieldName("key2")
            assertValue("value2")
            assertEndObject()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_map_items_with_anchor_and_alias() {
        createYamlReader("""
        |     - {hey: &anchor ha, ho: *anchor}
        """.trimMargin()).apply {
            assertStartArray()
            assertStartObject()
            assertFieldName("hey")
            assertValue("ha")
            assertFieldName("ho")
            assertValue("ha")
            assertEndObject()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun fail_on_duplicate_map_field_names() {
        createYamlReader("""
        |    {a: 1, a: 2}
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("a")
            assertValue(1, ValueType.Int)
            assertInvalidYaml()
        }
    }

    @Test
    fun read_map_and_sequence_in_map_items() {
        createYamlReader("""
        |     - {"key0",?key1: {e1: v1}, 'key2': [v1, v2]}
        """.trimMargin()).apply {
            assertStartArray()
            assertStartObject()
            assertFieldName("key0")
            assertValue(null)
            assertFieldName("?key1")
            assertStartObject()
            assertFieldName("e1")
            assertValue("v1")
            assertEndObject()
            assertFieldName("key2")
            assertStartArray()
            assertValue("v1")
            assertValue("v2")
            assertEndArray()
            assertEndObject()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_map_items_plain_string() {
        createYamlReader("""
        |     - {key0, key1: value1, key2: value2}
        """.trimMargin()).apply {
            assertStartArray()
            assertStartObject()
            assertFieldName("key0")
            assertValue(null)
            assertFieldName("key1")
            assertValue("value1")
            assertFieldName("key2")
            assertValue("value2")
            assertEndObject()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_map_items_plain_string_multiline() {
        createYamlReader("""
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
        """.trimMargin()).apply {
            assertStartArray()
            assertStartObject()
            assertFieldName("key0")
            assertValue(null)
            assertFieldName("key1")
            assertValue("value1")
            assertFieldName("key2")
            assertValue("value2 and longer and longer")
            assertEndObject()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_map_items_plain_string_wrong_multiline() {
        createYamlReader("""
        |     - {key0
        |     multiline
        """.trimMargin()).apply {
            assertStartArray()
            assertStartObject()
            assertInvalidYaml()
        }
    }

    @Test
    fun read_map_multiline_items() {
        createYamlReader("""
        |   {"key0",
        |"key1":
        |"value1",
        |'key2': 'value2'}
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key0")
            assertValue(null)
            assertFieldName("key1")
            assertValue("value1")
            assertFieldName("key2")
            assertValue("value2")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_map_with_explicit_key_items() {
        createYamlReader("""
        |   {? ,test: v1}
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName(null)
            assertValue(null)
            assertFieldName("test")
            assertValue("v1")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_map_with_explicit_direct_key_items() {
        createYamlReader("""
        |   {?,test: v1}
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName(null)
            assertValue(null)
            assertFieldName("test")
            assertValue("v1")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_map_with_explicit_defined_key_with_value_items() {
        createYamlReader("""
        |   {? t1: v0,test: v1}
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("t1")
            assertValue("v0")
            assertFieldName("test")
            assertValue("v1")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_map_with_explicit_defined_key_items() {
        createYamlReader("""
        |   {? t1}
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("t1")
            assertValue(null)
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_map_with_explicit_key_value_items() {
        createYamlReader("""
        |   {?: v0,test: v1}
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName(null)
            assertValue("v0")
            assertFieldName("test")
            assertValue("v1")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun fail_with_unfinished_map() {
        createYamlReader("""
        |   {?: v0,test: v1
        """.trimMargin()).apply{
            assertStartObject()
            assertFieldName(null)
            assertValue("v0")
            assertFieldName("test")
            assertValue("v1")
            assertInvalidYaml()
        }
    }

    @Test
    fun read_map_with_explicit_defined_sequence_key_with_value_items() {
        createYamlReader("""
        |   {? [a1]: v0,test: v1}
        """.trimMargin()).apply {
            assertStartObject()
            assertStartComplexFieldName()
            assertStartArray()
            assertValue("a1")
            assertEndArray()
            assertEndComplexFieldName()
            assertValue("v0")
            assertFieldName("test")
            assertValue("v1")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_map_with_explicit_defined_map_key_with_value_items() {
        createYamlReader("""
        |   {? {k1: v1}: v0,test: v1}
        """.trimMargin()).apply {
            assertStartObject()
            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("k1")
            assertValue("v1")
            assertEndObject()
            assertEndComplexFieldName()
            assertValue("v0")
            assertFieldName("test")
            assertValue("v1")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun fail_on_embedded_sequence() {
        createYamlReader("""- {"key0", - wrong}""").apply {
            assertStartArray()
            assertStartObject()
            assertFieldName("key0")
            assertValue(null)
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_wrong_sequence_end() {
        createYamlReader(""" - {key0: "v1"]""").apply {
            assertStartArray()
            assertStartObject()
            assertFieldName("key0")
            assertValue("v1")
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_double_explicit() {
        createYamlReader(""" - {? ? wrong""").apply {
            assertStartArray()
            assertStartObject()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_invalid_string_types() {
        createYamlReader("{|").apply {
            assertStartObject()
            assertInvalidYaml()
        }

        createYamlReader("{>").apply {
            assertStartObject()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_reserved_indicators() {
        createYamlReader("{@").apply {
            assertStartObject()
            assertInvalidYaml()
        }

        createYamlReader("{`").apply {
            assertStartObject()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_value_tag_on_map() {
        createYamlReader("!!str {k: v}").apply {
            assertInvalidYaml()
        }
    }
}
