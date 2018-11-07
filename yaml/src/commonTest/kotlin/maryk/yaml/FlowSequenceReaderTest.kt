package maryk.yaml

import kotlin.test.Test

class FlowSequenceReaderTest {
    @Test
    fun readSequenceItems() {
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
    fun readSequenceItemsWithAnchorAndAlias() {
        createYamlReader("[ &anchor ha, *anchor]").apply {
            assertStartArray()
            assertValue("ha")
            assertValue("ha")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun readSequenceItemsPlain() {
        createYamlReader("""
            |     - [test1, test2, test3, ?test4]
        """.trimMargin()).apply {
            assertStartArray()
            assertStartArray()
            assertValue("test1")
            assertValue("test2")
            assertValue("test3")
            assertValue("?test4")
            assertEndArray()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun readSequenceItemsWithSequencesAndMaps() {
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
    fun readSequenceItemsPlainMultiline() {
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
    fun readSequenceItemsPlainWrongMultiline() {
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
    fun readSequenceWithWhitespacingItems() {
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
    fun readSequenceWithMapItems() {
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
    fun readSequenceWithExplicitKeyItems() {
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
    fun readSequenceWithExplicitDirectKeyItems() {
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
    fun readSequenceWithExplicitDefinedKeyWithValueItems() {
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
    fun readSequenceWithExplicitDefinedKeyItems() {
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
    fun readSequenceWithExplicitKeyValueItems() {
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
    fun failWhenNotClosedSequence() {
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
    fun readSequenceWithExplicitDefinedSequenceKeyWithValueItems() {
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
    fun readSequenceWithExplicitDefinedMapKeyWithValueItems() {
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
    fun failOnEmbeddedSequence() {
        createYamlReader("""["key0", - wrong]""").apply {
            assertStartArray()
            assertValue("key0")
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnWrongSequenceEnd() {
        createYamlReader("""["v1"}""").apply {
            assertStartArray()
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnDoubleExplicit() {
        createYamlReader("[? ? wrong").apply {
            assertStartArray()
            assertStartObject()
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnInvalidStringTypes() {
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
    fun failOnReservedIndicators() {
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
    fun failOnValueTagOnSequence() {
        createYamlReader("!!str [1, 2]").apply {
            assertInvalidYaml()
        }
    }
}
