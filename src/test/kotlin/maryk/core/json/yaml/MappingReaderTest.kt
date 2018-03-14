package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForFieldName
import maryk.core.json.testForObjectEnd
import maryk.core.json.testForObjectStart
import maryk.core.json.testForValue
import maryk.test.shouldThrow
import kotlin.test.Test

class MappingReaderTest {
    @Test
    fun read_simple_mapping() {
        val reader = createYamlReader("""
        |key1: value1
        |'key2': "value2"
        |"key3": 'value3'
        |key4: "value4"
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key1")
        testForValue(reader, "value1")
        testForFieldName(reader, "key2")
        testForValue(reader, "value2")
        testForFieldName(reader, "key3")
        testForValue(reader, "value3")
        testForFieldName(reader, "key4")
        testForValue(reader, "value4")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_mapping_with_comments_and_line_breaks() {
        val reader = createYamlReader("""
        |key1: "value1" #comment at end
        |'key2': #comment after key
        |
        |  "value2"
        |
        |"key3": #comment after key
        |   #another comment line
        | #and another
        |  'value3'
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key1")
        testForValue(reader, "value1")
        testForFieldName(reader, "key2")
        testForValue(reader, "value2")
        testForFieldName(reader, "key3")
        testForValue(reader, "value3")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_indented_mapping() {
        val reader = createYamlReader("""
        |  key1: value1
        |  'key2': "value2"
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key1")
        testForValue(reader, "value1")
        testForFieldName(reader, "key2")
        testForValue(reader, "value2")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_wrong_mapping() {
        val reader = createYamlReader("""
        |key1: value1
        |'key2': value2
        |  "key3": 'value3'
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key1")
        testForValue(reader, "value1")
        testForFieldName(reader, "key2")
        shouldThrow<InvalidYamlContent> {
            reader.nextToken()
        }
    }

    @Test
    fun read_deeper_mapping() {
        val reader = createYamlReader("""
        |key1: value1
        |'key2':
        |  "key3": 'value3'
        |  key4: "value4"
        |key5:
        |   "value5"
        |key6: value6

        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key1")
        testForValue(reader, "value1")
        testForFieldName(reader, "key2")
        testForObjectStart(reader)
        testForFieldName(reader, "key3")
        testForValue(reader, "value3")
        testForFieldName(reader, "key4")
        testForValue(reader, "value4")
        testForObjectEnd(reader)
        testForFieldName(reader, "key5")
        testForValue(reader, "value5")
        testForFieldName(reader, "key6")
        testForValue(reader, "value6")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_mapping_with_array() {
        val reader = createYamlReader("""
        |key1: value1
        |'key2':
        |  - hey
        |  - "hoi"
        |key5: "value5"
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key1")
        testForValue(reader, "value1")
        testForFieldName(reader, "key2")
        testForArrayStart(reader)
        testForValue(reader, "hey")
        testForValue(reader, "hoi")
        testForArrayEnd(reader)
        testForFieldName(reader, "key5")
        testForValue(reader, "value5")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }
}