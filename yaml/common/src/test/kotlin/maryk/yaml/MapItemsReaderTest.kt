package maryk.yaml

import maryk.json.ValueType
import kotlin.test.Test

class MapItemsReaderTest {
    @Test
    fun read_simple_mapping() {
        createYamlReader("""
        |key1: value1
        |'key2': "value2"
        |"key3": 'value3'
        |key4: "value4"
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key1")
            assertValue("value1")
            assertFieldName("key2")
            assertValue("value2")
            assertFieldName("key3")
            assertValue("value3")
            assertFieldName("key4")
            assertValue("value4")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_mapping_with_comments_and_line_breaks() {
        createYamlReader("""
        |key1: "value1" #comment at end
        |'key2': #comment after key
        |
        |  "value2"
        |
        |"key3": #comment after key
        |   #another comment line
        | #and another
        |  'value3'
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key1")
            assertValue("value1")
            assertFieldName("key2")
            assertValue("value2")
            assertFieldName("key3")
            assertValue("value3")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_indented_mapping() {
        createYamlReader("""
        |  key1: value1
        |  'key2': "value2"
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key1")
            assertValue("value1")
            assertFieldName("key2")
            assertValue("value2")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_wrong_mapping() {
        createYamlReader("""
        |key1: value1
        |'key2': value2
        |  "key3": 'value3'
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key1")
            assertValue("value1")
            assertFieldName("key2")
            assertInvalidYaml()
        }
    }

    @Test
    fun read_deeper_mapping() {
        createYamlReader("""
        |key1: value1
        |'key2':
        |  "key3": 'value3'
        |  key4: "value4"
        |key5:
        |   "value5"
        |key6: value6

        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key1")
            assertValue("value1")
            assertFieldName("key2")
            assertStartObject()
            assertFieldName("key3")
            assertValue("value3")
            assertFieldName("key4")
            assertValue("value4")
            assertEndObject()
            assertFieldName("key5")
            assertValue("value5")
            assertFieldName("key6")
            assertValue("value6")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_mapping_with_array() {
        createYamlReader("""
        |key1: value1
        |'key2':
        |  - hey
        |  - "hoi"
        |key5: "value5"
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key1")
            assertValue("value1")
            assertFieldName("key2")
            assertStartArray()
            assertValue("hey")
            assertValue("hoi")
            assertEndArray()
            assertFieldName("key5")
            assertValue("value5")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_in_map() {
        val input = """
        | 1:
        |   seq:
        |   - a
        |   - b
        | 2: v2
        """.trimMargin()
        createYamlReader(input).apply {
            assertStartObject()

            assertFieldName("1")
            assertStartObject()
            assertFieldName("seq")
            assertStartArray()
            assertValue("a")
            assertValue("b")
            assertEndArray()
            assertEndObject()

            assertFieldName("2")
            assertValue("v2", ValueType.String)

            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun fail_duplicate_key() {
        val input = """
        | alfa: a
        | alfa: b
        """.trimMargin()
        createYamlReader(input).apply {
            assertStartObject()
            assertFieldName("alfa")
            assertValue("a")
            assertInvalidYaml()
        }
    }

    @Test
    fun read_map_in_array_at_end() {
        createYamlReader("""
        |  properties:
        |  - index: 0
        |    name: string
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("properties")

            assertStartArray()
            assertStartObject()
            assertFieldName("index")
            assertValue(0, ValueType.Int)
            assertFieldName("name")
            assertValue("string")
            assertEndObject()
            assertEndArray()
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_map_in_array() {
        createYamlReader("""
        |  properties:
        |  - index: 0
        |    name: string
        |  - test
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("properties")

            assertStartArray()
            assertStartObject()
            assertFieldName("index")
            assertValue(0, ValueType.Int)
            assertFieldName("name")
            assertValue("string")
            assertEndObject()
            assertValue("test")
            assertEndArray()
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_map_with_null_value_in_array() {
        createYamlReader("""
        - k1:
        - k2:

        """.trimIndent()).apply {
            assertStartArray()
            assertStartObject()
            assertFieldName("k1")
            assertValue(null, ValueType.Null)
            assertEndObject()
            assertStartObject()
            assertFieldName("k2")
            assertValue(null, ValueType.Null)
            assertEndObject()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_indented_map_in_array() {
        createYamlReader("""
        |  -        index: 0
        |           name: string
        |  - test
        """.trimMargin()).apply {
            assertStartArray()
            assertStartObject()
            assertFieldName("index")
            assertValue(0, ValueType.Int)
            assertFieldName("name")
            assertValue("string")
            assertEndObject()
            assertValue("test")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun fail_on_double_key() {
        createYamlReader("""
        |  index: 0: 1
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("index")
            assertInvalidYaml()
        }
    }

    @Test
    fun null_values() {
        createYamlReader("""
        |  key1:
        |  key2:
        |  - true
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key1")
            assertValue(null, ValueType.Null)
            assertFieldName("key2")
            assertStartArray()
            assertValue(true, ValueType.Bool)
            assertEndArray()
            assertEndObject()
            assertEndDocument()
        }
    }
}
