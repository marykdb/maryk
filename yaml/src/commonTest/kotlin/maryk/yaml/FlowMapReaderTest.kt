package maryk.yaml

import maryk.json.ValueType
import kotlin.test.Test

class FlowMapReaderTest {
    @Test
    fun readMapItems() {
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
    fun readMapItemsWithAnchorAndAlias() {
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
    fun failOnDuplicateMapFieldNames() {
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
    fun readMapAndSequenceInMapItems() {
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
    fun readMapItemsPlainString() {
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
    fun readMapItemsPlainStringMultiline() {
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
    fun readMapItemsPlainStringWrongMultiline() {
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
    fun readMapMultilineItems() {
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
    fun readMapWithExplicitKeyItems() {
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
    fun readMapWithExplicitDirectKeyItems() {
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
    fun readMapWithExplicitDefinedKeyWithValueItems() {
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
    fun readMapWithExplicitDefinedKeyItems() {
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
    fun readMapWithExplicitKeyValueItems() {
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
    fun failWithUnfinishedMap() {
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
    fun readMapWithExplicitDefinedSequenceKeyWithValueItems() {
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
    fun readMapWithExplicitDefinedMapKeyWithValueItems() {
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
    fun failOnEmbeddedSequence() {
        createYamlReader("""- {"key0", - wrong}""").apply {
            assertStartArray()
            assertStartObject()
            assertFieldName("key0")
            assertValue(null)
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnWrongSequenceEnd() {
        createYamlReader(""" - {key0: "v1"]""").apply {
            assertStartArray()
            assertStartObject()
            assertFieldName("key0")
            assertValue("v1")
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnDoubleExplicit() {
        createYamlReader(""" - {? ? wrong""").apply {
            assertStartArray()
            assertStartObject()
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnInvalidStringTypes() {
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
    fun failOnReservedIndicators() {
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
    fun failOnValueTagOnMap() {
        createYamlReader("!!str {k: v}").apply {
            assertInvalidYaml()
        }
    }
}
