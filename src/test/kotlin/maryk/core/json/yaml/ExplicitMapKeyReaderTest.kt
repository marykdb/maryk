package maryk.core.json.yaml

import maryk.core.json.testForEndJson
import maryk.core.json.testForFieldName
import maryk.core.json.testForObjectEnd
import maryk.core.json.testForObjectStart
import maryk.core.json.testForObjectValue
import kotlin.test.Test

class ExplicitMapKeyReaderTest {
    @Test
    fun empty_key_indicator() {
        val reader = createYamlReader("?")
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForObjectValue(reader, null)
        testForObjectEnd(reader)
        testForEndJson(reader)
    }

    @Test
    fun empty_key_indicator_with_value() {
        val reader = createYamlReader("""
        | ?
        | : value
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForObjectValue(reader, "value")
        testForObjectEnd(reader)
        testForEndJson(reader)
    }

    @Test
    fun empty_key_indicator_with_no_value() {
        val reader = createYamlReader("""
        | ?
        | key: value
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForObjectValue(reader, null)
        testForFieldName(reader, "key")
        testForObjectValue(reader, "value")
        testForObjectEnd(reader)
        testForEndJson(reader)
    }

    @Test
    fun plain_string_key_indicator_with_value() {
        val reader = createYamlReader("""
        | ? key
        | : value
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key")
        testForObjectValue(reader, "value")
        testForObjectEnd(reader)
        testForEndJson(reader)
    }
}