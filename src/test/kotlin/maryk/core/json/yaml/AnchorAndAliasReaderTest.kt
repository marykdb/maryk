package maryk.core.json.yaml

import maryk.core.json.ValueType
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

class AnchorAndAliasReaderTest {
    @Test
    fun anchors_with_value() {
        createYamlReader("""
        |  - &array alfa
        |  - *array
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("alfa")
            assertValue("alfa")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun anchors_in_sequences() {
        createYamlReader("""
        |  - &array [a, b]
        |  - *array
        """.trimMargin()).apply {
            assertStartArray()
            assertStartArray()
            assertValue("a")
            assertValue("b")
            assertEndArray()
            assertStartArray()
            assertValue("a")
            assertValue("b")
            assertEndArray()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun anchors_complex() {
        createYamlReader("""
        |  - &complex
        |       test: {a: b}
        |       2: [1,2, {[a]}]
        |       3:
        |           - a
        |  - *complex
        |  - blaat
        """.trimMargin()).apply {

            assertStartArray()

            // twice the same
            (0..1).forEach {
                assertStartObject()
                assertFieldName("test")
                assertStartObject()
                assertFieldName("a")
                assertValue("b")
                assertEndObject()
                assertFieldName("2")
                assertStartArray()
                assertValue(1, ValueType.Int)
                assertValue(2, ValueType.Int)
                assertStartObject()
                assertStartComplexFieldName()
                assertStartArray()
                assertValue("a")
                assertEndArray()
                assertEndComplexFieldName()
                assertValue(null, ValueType.Null)
                assertEndObject()
                assertEndArray()
                assertFieldName("3")
                assertStartArray()
                assertValue("a")
                assertEndArray()
                assertEndObject()
            }

            assertValue("blaat")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun fail_on_invalid_anchor() {
        createYamlReader("""
        |  - & [a, b]
        """.trimMargin()).apply {
            assertStartArray()
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_invalid_alias() {
        createYamlReader("""
        |  - &array a
        |  - *
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("a")
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_unknown_alias() {
        createYamlReader("""
        |  - &array a
        |  - *unknown
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("a")
            assertInvalidYaml()
        }
    }
}
