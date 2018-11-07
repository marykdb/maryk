package maryk.yaml

import maryk.json.ValueType
import kotlin.test.Test

class MapItemsReaderTest {
    @Test
    fun readSimpleMapping() {
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
    fun readMappingWithCommentsAndLineBreaks() {
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
    fun readIndentedMapping() {
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
    fun readWrongMapping() {
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
    fun readDeeperMapping() {
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
    fun readMappingWithArray() {
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
    fun readSequenceInMap() {
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
    fun failDuplicateKey() {
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
    fun readMapInArrayAtEnd() {
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
    fun readMapInArray() {
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
    fun readMapWithNullValueInArray() {
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
    fun readIndentedMapInArray() {
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
    fun failOnDoubleKey() {
        createYamlReader("""
        |  index: 0: 1
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("index")
            assertInvalidYaml()
        }
    }

    @Test
    fun nullValues() {
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
