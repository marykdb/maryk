package maryk.core.json.yaml

import maryk.core.json.assertEndArray
import maryk.core.json.assertEndComplexFieldName
import maryk.core.json.assertEndDocument
import maryk.core.json.assertEndObject
import maryk.core.json.assertFieldName
import maryk.core.json.assertInvalidYaml
import maryk.core.json.assertStartArray
import maryk.core.json.assertStartComplexFieldName
import maryk.core.json.assertStartObject
import maryk.core.json.assertValue
import kotlin.test.Test

class FlowSequenceReaderTest {
    @Test
    fun read_sequence_items() {
        createYamlReader("""
            |     - ["test1", "test2", 'test3']
        """.trimMargin()).apply {
            assertStartArray()
            assertStartArray()
            assertValue("test1")
            assertValue("test2")
            assertValue("test3")
            assertEndArray()
            assertEndArray()
            assertEndDocument()
        }
    }


    @Test
    fun read_sequence_items_with_anchor_and_alias() {
        createYamlReader("[ &anchor ha, *anchor]").apply {
            assertStartArray()
            assertValue("ha")
            assertValue("ha")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_items_plain() {
        createYamlReader("""
            |     - [test1, test2, test3]
        """.trimMargin()).apply {
            assertStartArray()
            assertStartArray()
            assertValue("test1")
            assertValue("test2")
            assertValue("test3")
            assertEndArray()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_items_with_sequences_and_maps() {
        createYamlReader("""
            |     - [test1, [t1, t2], {k: v}]
        """.trimMargin()).apply {
            assertStartArray()
            assertStartArray()
            assertValue("test1")
            assertStartArray()
            assertValue("t1")
            assertValue("t2")
            assertEndArray()
            assertStartObject()
            assertFieldName("k")
            assertValue("v")
            assertEndObject()
            assertEndArray()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_items_plain_multiline() {
        createYamlReader("""
            |     - [test1
            |      longer
            |      and longer,
            |       -test2,
            |       test3]
        """.trimMargin()).apply {
            assertStartArray()
            assertStartArray()
            assertValue("test1 longer and longer")
            assertValue("-test2")
            assertValue("test3")
            assertEndArray()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_items_plain_wrong_multiline() {
        createYamlReader("""
            |     - [test1
            |     wrong]
        """.trimMargin()).apply {
            assertStartArray()
            assertStartArray()
            assertInvalidYaml()
        }
    }

    @Test
    fun read_sequence_with_whitespacing_items() {
        createYamlReader("""
            |     - ["test1"    ,    "test2",
            |"test3"  ]
        """.trimMargin()).apply{
            assertStartArray()
            assertStartArray()
            assertValue("test1")
            assertValue("test2")
            assertValue("test3")
            assertEndArray()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_with_map_items() {
        createYamlReader("""
            |     - [t1: v1, t2: v2]
        """.trimMargin()).apply{
            assertStartArray()
            assertStartArray()
            assertStartObject()
            assertFieldName("t1")
            assertValue("v1")
            assertEndObject()
            assertStartObject()
            assertFieldName("t2")
            assertValue("v2")
            assertEndObject()
            assertEndArray()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_with_explicit_key_items() {
        createYamlReader("""
        |   [? ,test]
        """.trimMargin()).apply{
            assertStartArray()
            assertStartObject()
            assertFieldName(null)
            assertValue(null)
            assertEndObject()
            assertValue("test")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_with_explicit_direct_key_items() {
        createYamlReader("""
        |   [?,test]
        """.trimMargin()).apply{
            assertStartArray()
            assertStartObject()
            assertFieldName(null)
            assertValue(null)
            assertEndObject()
            assertValue("test")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_with_explicit_defined_key_with_value_items() {
        createYamlReader("""
        |   [? t1: v0,test]
        """.trimMargin()).apply{
            assertStartArray()
            assertStartObject()
            assertFieldName("t1")
            assertValue("v0")
            assertEndObject()
            assertValue("test")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_with_explicit_defined_key_items() {
        createYamlReader("""
        |   [? t1]
        """.trimMargin()).apply{
            assertStartArray()
            assertStartObject()
            assertFieldName("t1")
            assertValue(null)
            assertEndObject()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_with_explicit_key_value_items() {
        createYamlReader("""
        |   [?: v0,test]
        """.trimMargin()).apply{
            assertStartArray()
            assertStartObject()
            assertFieldName(null)
            assertValue("v0")
            assertEndObject()
            assertValue("test")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun fail_when_not_closed_sequence() {
        createYamlReader("""
        |   [?: v0
        """.trimMargin()).apply{
            assertStartArray()
            assertStartObject()
            assertFieldName(null)
            assertValue("v0")
            assertInvalidYaml()
        }
    }

    @Test
    fun read_sequence_with_explicit_defined_sequence_key_with_value_items() {
        createYamlReader("""
        |   [? [a1, a2]: v0,test]
        """.trimMargin()).apply{
            assertStartArray()
            assertStartObject()
            assertStartComplexFieldName()
            assertStartArray()
            assertValue("a1")
            assertValue("a2")
            assertEndArray()
            assertEndComplexFieldName()
            assertValue("v0")
            assertEndObject()
            assertValue("test")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun read_sequence_with_explicit_defined_map_key_with_value_items() {
        createYamlReader("""
        |   [? {k1: v1}: v0,test]
        """.trimMargin()).apply{
            assertStartArray()
            assertStartObject()
            assertStartComplexFieldName()
            assertStartObject()
            assertFieldName("k1")
            assertValue("v1")
            assertEndObject()
            assertEndComplexFieldName()
            assertValue("v0")
            assertEndObject()
            assertValue("test")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun fail_on_embedded_sequence() {
        createYamlReader("""["key0", - wrong]""").apply {
            assertStartArray()
            assertValue("key0")
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_wrong_sequence_end() {
        createYamlReader("""["v1"}""").apply {
            assertStartArray()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_double_explicit() {
        createYamlReader("[? ? wrong").apply {
            assertStartArray()
            assertStartObject()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_invalid_string_types() {
        createYamlReader("[|").apply {
            assertStartArray()
            assertInvalidYaml()
        }

        createYamlReader("[>").apply {
            assertStartArray()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_reserved_indicators() {
        createYamlReader("[@").apply {
            assertStartArray()
            assertInvalidYaml()
        }

        createYamlReader("[`").apply {
            assertStartArray()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_value_tag_on_sequence() {
        createYamlReader("!!str [1, 2]").apply {
            assertInvalidYaml()
        }
    }
}
