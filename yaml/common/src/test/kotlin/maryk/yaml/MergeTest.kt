package maryk.yaml

import kotlin.test.Test

class MergeTest{
    @Test
    fun test_map_merge() {
        createYamlReader("""
        |key1: value1
        |<<: { key2: b, key3: c }
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key1")
            assertValue("value1")
            assertFieldName("key2")
            assertValue("b")
            assertFieldName("key3")
            assertValue("c")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun test_map_in_array_merge() {
        createYamlReader("""
        |key1: value1
        |<<: [{ key2: b }, {key3: c }]
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key1")
            assertValue("value1")
            assertFieldName("key2")
            assertValue("b")
            assertFieldName("key3")
            assertValue("c")
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun test_map_in_array_with_alias_merge() {
        createYamlReader("""
        |key1: &map { key2: b, key3: c }
        |<<: *map
        """.trimMargin()).apply {
            assertStartObject()
            assertFieldName("key1")

            assertStartObject()
            assertFieldName("key2")
            assertValue("b")
            assertFieldName("key3")
            assertValue("c")
            assertEndObject()

            assertFieldName("key2")
            assertValue("b")
            assertFieldName("key3")
            assertValue("c")

            assertEndObject()
            assertEndDocument()
        }
    }
}
