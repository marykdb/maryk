package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForComplexFieldNameEnd
import maryk.core.json.testForComplexFieldNameStart
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

    @Test
    fun map_multiline_key_indicator_with_value() {
        val reader = createYamlReader("""
        | ? k1: v1
        |   k2: v2
        | : value
        """.trimMargin())
        testForObjectStart(reader)
        testForComplexFieldNameStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForValue(reader, "v1")
        testForFieldName(reader, "k2")
        testForValue(reader, "v2")
        testForObjectEnd(reader)
        testForComplexFieldNameEnd(reader)
        testForValue(reader, "value")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun map_multiline_key_indicator_on_new_line_with_value() {
        val reader = createYamlReader("""
        | ?
        |     k1: v1
        |     k2: v2
        | : value
        """.trimMargin())
        testForObjectStart(reader)
        testForComplexFieldNameStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForValue(reader, "v1")
        testForFieldName(reader, "k2")
        testForValue(reader, "v2")
        testForObjectEnd(reader)
        testForComplexFieldNameEnd(reader)
        testForValue(reader, "value")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun map_multiline_key_indicator_with_value_and_double_quotes() {
        val reader = createYamlReader("""
        | ?    "k1": "v1"
        |      "k2": "v2"
        | : "value"
        """.trimMargin())
        testForObjectStart(reader)
        testForComplexFieldNameStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForValue(reader, "v1")
        testForFieldName(reader, "k2")
        testForValue(reader, "v2")
        testForObjectEnd(reader)
        testForComplexFieldNameEnd(reader)
        testForValue(reader, "value")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun sequence_multiline_key_indicator_with_value() {
        val reader = createYamlReader("""
        | ? - a1
        |   - a2
        | : value
        """.trimMargin())
        testForObjectStart(reader)
        testForComplexFieldNameStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "a1")
        testForValue(reader, "a2")
        testForArrayEnd(reader)
        testForComplexFieldNameEnd(reader)
        testForValue(reader, "value")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun interrupt_sequence_within_explicit_map_key() {
        val reader = createYamlReader("""
        | ? - a1
        """.trimMargin())
        testForObjectStart(reader)
        testForComplexFieldNameStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "a1")
        testForArrayEnd(reader)
        testForComplexFieldNameEnd(reader)
    }

    @Test
    fun interrupt_map_within_explicit_map_key() {
        val reader = createYamlReader("""
        | ? k1: v1
        """.trimMargin())
        testForObjectStart(reader)
        testForComplexFieldNameStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForValue(reader, "v1")
        testForObjectEnd(reader)
        testForComplexFieldNameEnd(reader)
    }
}
