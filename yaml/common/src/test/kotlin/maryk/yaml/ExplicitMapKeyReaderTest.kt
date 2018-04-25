package maryk.yaml

import maryk.json.ArrayType
import maryk.json.MapType
import maryk.json.ValueType
import kotlin.test.Test

class ExplicitMapKeyReaderTest {
    @Test
    fun empty_key_indicator() {
        createYamlReader("?").apply {
            assertStartObject()
            assertFieldName(null)
            assertValue(null)
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun empty_key_indicator_with_value() {
        createYamlReader("""
        | ?
        | : value
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName(null)
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun empty_key_indicator_with_no_value() {
        createYamlReader("""
        | ?
        | key: value
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName(null)
            assertValue(null)
            assertFieldName("key")
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun plain_string_key_indicator_with_value() {
        createYamlReader("""
        | ? key
        | : value
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key")
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun plain_string_multiline_key_indicator_with_value() {
        createYamlReader("""
        | ? key
        |   with more lines
        | : value
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key with more lines")
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun map_in_key_indicator_with_value() {
        createYamlReader("""
        | ? k1: v1
        |   k2: v2
        | : value
        """.trimMargin()).apply {
            assertStartObject()
            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("k1")
            assertValue("v1")
            assertFieldName("k2")
            assertValue("v2")
            assertEndObject()
            assertEndComplexFieldName()
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun map_in_key_indicator_on_new_line_with_value() {
        createYamlReader("""
        | ?
        |     k1: v1
        |     k2: v2
        | : value
        """.trimMargin()).apply {
            assertStartObject()
            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("k1")
            assertValue("v1")
            assertFieldName("k2")
            assertValue("v2")
            assertEndObject()
            assertEndComplexFieldName()
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun map_in_key_indicator_with_value_and_only_tag() {
        createYamlReader("""
        | ? k1: v1
        | : !!omap
        | ? k2: v2
        | : !!set
        | ? k3: v3
        | : !!str
        | ? k4: v4
        | : !!pairs
        """.trimMargin()).apply {
            assertStartObject()

            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("k1")
            assertValue("v1")
            assertEndObject()
            assertEndComplexFieldName()
            assertStartObject(MapType.OrderedMap)
            assertEndObject()

            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("k2")
            assertValue("v2")
            assertEndObject()
            assertEndComplexFieldName()
            assertStartArray(ArrayType.Set)
            assertEndArray()

            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("k3")
            assertValue("v3")
            assertEndObject()
            assertEndComplexFieldName()
            assertValue(null, ValueType.Null)

            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("k4")
            assertValue("v4")
            assertEndObject()
            assertEndComplexFieldName()
            assertStartObject(MapType.Pairs)
            assertEndObject()

            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun map_in_key_indicator_with_value_and_double_quotes() {
        createYamlReader("""
        | ?    "k1": "v1"
        |      "k2": "v2"
        | : "value"
        """.trimMargin()).apply {
            assertStartObject()
            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("k1")
            assertValue("v1")
            assertFieldName("k2")
            assertValue("v2")
            assertEndObject()
            assertEndComplexFieldName()
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun sequence_in_key_indicator_with_value() {
        createYamlReader("""
        | ? - a1
        |   - a2
        | : value
        """.trimMargin()).apply {
            assertStartObject()
            assertStartComplexFieldName()
            assertStartArray()
            assertValue("a1")
            assertValue("a2")
            assertEndArray()
            assertEndComplexFieldName()
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun sequence_with_new_line_in_key_indicator_with_value() {
        createYamlReader("""
        | ?
        |   - a1
        |   - a2
        | : value
        """.trimMargin()).apply {
            assertStartObject()
            assertStartComplexFieldName()
            assertStartArray()
            assertValue("a1")
            assertValue("a2")
            assertEndArray()
            assertEndComplexFieldName()
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun interrupt_sequence_within_explicit_map_key() {
        createYamlReader("""
        | ? - a1
        """.trimMargin()).apply {
            assertStartObject()
            assertStartComplexFieldName()
            assertStartArray()
            assertValue("a1")
            assertEndArray()
            assertEndComplexFieldName()
        }
    }

    @Test
    fun interrupt_map_within_explicit_map_key() {
        createYamlReader("""
        | ? k1: v1
        """.trimMargin()).apply {
            assertStartObject()
            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("k1")
            assertValue("v1")
            assertEndObject()
            assertEndComplexFieldName()
        }
    }

    @Test
    fun flow_sequence_with_new_line_in_key_indicator_with_value() {
        createYamlReader("""
        | ? [ a1, a2]
        | : value
        """.trimMargin()).apply {
            assertStartObject()
            assertStartComplexFieldName()
            assertStartArray()
            assertValue("a1")
            assertValue("a2")
            assertEndArray()
            assertEndComplexFieldName()
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun flow_map_in_key_indicator_with_value() {
        createYamlReader("""
        | ? { k1: v1,
        |     k2: v2 }
        | : value
        """.trimMargin()).apply {
            assertStartObject()
            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("k1")
            assertValue("v1")
            assertFieldName("k2")
            assertValue("v2")
            assertEndObject()
            assertEndComplexFieldName()
            assertValue("value")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun second_be_explicit_key() {
        createYamlReader("""
        | k1: v1
        | ? k2
        | : v2
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("k1")
            assertValue("v1")
            assertFieldName("k2")
            assertValue("v2")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun read_properties() {
        createYamlReader("""
        |properties:
        | ? 0: string
        | : !Foo
        |   indexed: false
        |   regEx: ha.*
        | ? 1: model
        | : !Bar
        |   dataModel: SubMarykObject
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("properties")
            assertStartObject()
            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("0")
            assertValue("string")
            assertEndObject()
            assertEndComplexFieldName()
            assertStartObject(TestType.Foo)
            assertFieldName("indexed")
            assertValue(false, ValueType.Bool)
            assertFieldName("regEx")
            assertValue("ha.*")
            assertEndObject()
            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("1")
            assertValue("model")
            assertEndObject()
            assertEndComplexFieldName()
            assertStartObject(TestType.Bar)
            assertFieldName("dataModel")
            assertValue("SubMarykObject")
            assertEndObject()
            assertEndObject()
            assertEndObject()
            assertEndDocument()
        }
    }
}
