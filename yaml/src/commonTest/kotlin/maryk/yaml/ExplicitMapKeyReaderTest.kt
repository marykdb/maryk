package maryk.yaml

import maryk.json.ArrayType
import maryk.json.MapType
import maryk.json.ValueType
import kotlin.test.Test

class ExplicitMapKeyReaderTest {
    @Test
    fun emptyKeyIndicator() {
        createYamlReader("?").apply {
            assertStartObject()
            assertFieldName(null)
            assertValue(null)
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun emptyKeyIndicatorWithValue() {
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
    fun emptyKeyIndicatorWithNoValue() {
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
    fun plainStringKeyIndicatorWithValue() {
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
    fun plainStringMultilineKeyIndicatorWithValue() {
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
    fun mapInKeyIndicatorWithValue() {
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
    fun mapInKeyIndicatorOnNewLineWithValue() {
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
    fun mapInKeyIndicatorWithValueAndOnlyTag() {
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
    fun mapInKeyIndicatorWithValueAndDoubleQuotes() {
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
    fun sequenceInKeyIndicatorWithValue() {
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
    fun sequenceWithNewLineInKeyIndicatorWithValue() {
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
    fun interruptSequenceWithinExplicitMapKey() {
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
    fun interruptMapWithinExplicitMapKey() {
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
    fun flowSequenceWithNewLineInKeyIndicatorWithValue() {
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
    fun flowMapInKeyIndicatorWithValue() {
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
    fun secondBeExplicitKey() {
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
    fun readProperties() {
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
