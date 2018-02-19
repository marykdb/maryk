package maryk.core.json.yaml

import maryk.core.json.testForEndJson
import maryk.core.json.testForFieldName
import maryk.core.json.testForObjectEnd
import maryk.core.json.testForObjectStart
import maryk.core.json.testForObjectValue
import kotlin.test.Test

class MappingReaderTest {
    @Test
    fun read_simple_object() {
        val reader = createYamlReader("""
        |key1: value1
        |'key2': "value2"
        |"key3": 'value3'
        |key4: "value4"
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key1")
        testForObjectValue(reader, "value1")
        testForFieldName(reader, "key2")
        testForObjectValue(reader, "value2")
        testForFieldName(reader, "key3")
        testForObjectValue(reader, "value3")
        testForFieldName(reader, "key4")
        testForObjectValue(reader, "value4")
        testForObjectEnd(reader)
        testForEndJson(reader)
    }
}