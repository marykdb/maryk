package maryk.core.json.yaml

import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForFieldName
import maryk.core.json.testForObjectEnd
import maryk.core.json.testForObjectStart
import maryk.core.json.testForValue
import kotlin.test.Test

class ExplicitMapKeyReaderTest {
    @Test
    fun empty_key_indicator() {
        val reader = createYamlReader("?")
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForValue(reader, null)
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun empty_key_indicator_with_value() {
        val reader = createYamlReader("""
        | ?
        | : value
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForValue(reader, "value")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun empty_key_indicator_with_no_value() {
        val reader = createYamlReader("""
        | ?
        | key: value
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForValue(reader, null)
        testForFieldName(reader, "key")
        testForValue(reader, "value")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun plain_string_key_indicator_with_value() {
        val reader = createYamlReader("""
        | ? key
        | : value
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key")
        testForValue(reader, "value")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun plain_string_multiline_key_indicator_with_value() {
        val reader = createYamlReader("""
        | ? key
        |   with more lines
        | : value
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key with more lines")
        testForValue(reader, "value")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }
}