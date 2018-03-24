package maryk.core.json.yaml

import maryk.core.json.ValueType
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

class AnchorAndAliasReaderTest {
    @Test
    fun anchors_with_value() {
        val reader = createYamlReader("""
        |  - &array alfa
        |  - *array
        """.trimMargin())

        testForArrayStart(reader)
        testForValue(reader, "alfa")
        testForValue(reader, "alfa")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun anchors_in_sequences() {
        val reader = createYamlReader("""
        |  - &array [a, b]
        |  - *array
        """.trimMargin())

        testForArrayStart(reader)
        testForArrayStart(reader)
        testForValue(reader, "a")
        testForValue(reader, "b")
        testForArrayEnd(reader)
        testForArrayStart(reader)
        testForValue(reader, "a")
        testForValue(reader, "b")
        testForArrayEnd(reader)
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun anchors_complex() {
        val reader = createYamlReader("""
        |  - &complex
        |       test: {a: b}
        |       2: [1,2, {[a]}]
        |  - *complex
        """.trimMargin())

        testForArrayStart(reader)

        // twice the same
        (0..1).forEach {
            testForObjectStart(reader)
            testForFieldName(reader, "test")
            testForObjectStart(reader)
            testForFieldName(reader, "a")
            testForValue(reader, "b")
            testForObjectEnd(reader)
            testForFieldName(reader, "2")
            testForArrayStart(reader)
            testForValue(reader, 1, ValueType.Int)
            testForValue(reader, 2, ValueType.Int)
            testForObjectStart(reader)
            testForComplexFieldNameStart(reader)
            testForArrayStart(reader)
            testForValue(reader, "a")
            testForArrayEnd(reader)
            testForComplexFieldNameEnd(reader)
            testForValue(reader, null, ValueType.Null)
            testForObjectEnd(reader)
            testForArrayEnd(reader)
            testForObjectEnd(reader)
        }

        testForArrayEnd(reader)
        testForDocumentEnd(reader)

    }

    @Test
    fun fail_on_invalid_anchor() {
        val reader = createYamlReader("""
        |  - & [a, b]
        """.trimMargin())

        testForArrayStart(reader)
        testForInvalidYaml(reader)
    }

    @Test
    fun fail_on_invalid_alias() {
        val reader = createYamlReader("""
        |  - &array a
        |  - *
        """.trimMargin())

        testForArrayStart(reader)
        testForValue(reader, "a")
        testForInvalidYaml(reader)
    }

    @Test
    fun fail_on_unknown_alias() {
        val reader = createYamlReader("""
        |  - &array a
        |  - *unknown
        """.trimMargin())

        testForArrayStart(reader)
        testForValue(reader, "a")
        testForInvalidYaml(reader)
    }
}
