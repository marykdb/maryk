package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForComplexFieldNameEnd
import maryk.core.json.testForComplexFieldNameStart
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForFieldName
import maryk.core.json.testForInvalidYaml
import maryk.core.json.testForObjectEnd
import maryk.core.json.testForObjectStart
import maryk.core.json.testForValue
import kotlin.test.Test

class FlowSequenceReaderTest {
    @Test
    fun read_sequence_items() {
        val reader = createYamlReader("""
            |     - ["test1", "test2", "test3"]
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "test1")
        testForValue(reader, "test2")
        testForValue(reader, "test3")
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_sequence_items_plain() {
        val reader = createYamlReader("""
            |     - [test1, test2, test3]
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "test1")
        testForValue(reader, "test2")
        testForValue(reader, "test3")
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_sequence_items_with_sequences_and_maps() {
        val reader = createYamlReader("""
            |     - [test1, [t1, t2], {k: v}]
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "test1")
        testForArrayStart(reader)
        testForValue(reader, "t1")
        testForValue(reader, "t2")
        testForArrayEnd(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "k")
        testForValue(reader, "v")
        testForObjectEnd(reader)
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_sequence_items_plain_multiline() {
        val reader = createYamlReader("""
            |     - [test1
            |      longer
            |      and longer,
            |       -test2,
            |       test3]
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "test1 longer and longer")
        testForValue(reader, "-test2")
        testForValue(reader, "test3")
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_sequence_items_plain_wrong_multiline() {
        val reader = createYamlReader("""
            |     - [test1
            |     wrong]
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForInvalidYaml(reader)
    }

    @Test
    fun read_sequence_with_whitespacing_items() {
        val reader = createYamlReader("""
            |     - ["test1"    ,    "test2",
            |"test3"  ]
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "test1")
        testForValue(reader, "test2")
        testForValue(reader, "test3")
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_sequence_with_map_items() {
        val reader = createYamlReader("""
            |     - [t1: v1, t2: v2]
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "t1")
        testForValue(reader, "v1")
        testForObjectEnd(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "t2")
        testForValue(reader, "v2")
        testForObjectEnd(reader)
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_sequence_with_explicit_key_items() {
        val reader = createYamlReader("""
        |   [? ,test]
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForValue(reader, null)
        testForObjectEnd(reader)
        testForValue(reader, "test")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_sequence_with_explicit_direct_key_items() {
        val reader = createYamlReader("""
        |   [?,test]
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForValue(reader, null)
        testForObjectEnd(reader)
        testForValue(reader, "test")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_sequence_with_explicit_defined_key_with_value_items() {
        val reader = createYamlReader("""
        |   [? t1: v0,test]
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "t1")
        testForValue(reader, "v0")
        testForObjectEnd(reader)
        testForValue(reader, "test")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_sequence_with_explicit_defined_key_items() {
        val reader = createYamlReader("""
        |   [? t1]
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "t1")
        testForValue(reader, null)
        testForObjectEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_sequence_with_explicit_key_value_items() {
        val reader = createYamlReader("""
        |   [?: v0,test]
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForValue(reader, "v0")
        testForObjectEnd(reader)
        testForValue(reader, "test")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun fail_when_not_closed_sequence() {
        val reader = createYamlReader("""
        |   [?: v0
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, null)
        testForValue(reader, "v0")
        testForInvalidYaml(reader)
    }

    @Test
    fun read_sequence_with_explicit_defined_sequence_key_with_value_items() {
        val reader = createYamlReader("""
        |   [? [a1, a2]: v0,test]
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForComplexFieldNameStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "a1")
        testForValue(reader, "a2")
        testForArrayEnd(reader)
        testForComplexFieldNameEnd(reader)
        testForValue(reader, "v0")
        testForObjectEnd(reader)
        testForValue(reader, "test")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_sequence_with_explicit_defined_map_key_with_value_items() {
        val reader = createYamlReader("""
        |   [? {k1: v1}: v0,test]
        """.trimMargin())
        testForArrayStart(reader)
        testForObjectStart(reader)
        testForComplexFieldNameStart(reader)
        testForObjectStart(reader)
        testForFieldName(reader, "k1")
        testForValue(reader, "v1")
        testForObjectEnd(reader)
        testForComplexFieldNameEnd(reader)
        testForValue(reader, "v0")
        testForObjectEnd(reader)
        testForValue(reader, "test")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }
}
