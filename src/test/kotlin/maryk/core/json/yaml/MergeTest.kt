package maryk.core.json.yaml

import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForFieldName
import maryk.core.json.testForObjectEnd
import maryk.core.json.testForObjectStart
import maryk.core.json.testForValue
import kotlin.test.Test

class MergeTest{
    @Test
    fun test_map_merge() {
        val reader = createYamlReader("""
        |key1: value1
        |<<: { key2: b, key3: c }
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key1")
        testForValue(reader, "value1")
        testForFieldName(reader, "key2")
        testForValue(reader, "b")
        testForFieldName(reader, "key3")
        testForValue(reader, "c")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun test_map_in_array_merge() {
        val reader = createYamlReader("""
        |key1: value1
        |<<: [{ key2: b }, {key3: c }]
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key1")
        testForValue(reader, "value1")
        testForFieldName(reader, "key2")
        testForValue(reader, "b")
        testForFieldName(reader, "key3")
        testForValue(reader, "c")
        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun test_map_in_array_with_alias_merge() {
        val reader = createYamlReader("""
        |key1: &map { key2: b, key3: c }
        |<<: *map
        """.trimMargin())
        testForObjectStart(reader)
        testForFieldName(reader, "key1")

        testForObjectStart(reader)
        testForFieldName(reader, "key2")
        testForValue(reader, "b")
        testForFieldName(reader, "key3")
        testForValue(reader, "c")
        testForObjectEnd(reader)

        testForFieldName(reader, "key2")
        testForValue(reader, "b")
        testForFieldName(reader, "key3")
        testForValue(reader, "c")

        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }
}