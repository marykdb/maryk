package maryk.yaml

import maryk.json.ValueType
import kotlin.test.Test

class AnchorAndAliasReaderTest {
    @Test
    fun anchorsWithValue() {
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
    fun anchorsInSequences() {
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
    fun anchorsComplex() {
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
            for (it in 0..1) {
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
    fun failOnInvalidAnchor() {
        createYamlReader("""
        |  - & [a, b]
        """.trimMargin()).apply {
            assertStartArray()
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnInvalidAlias() {
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
    fun failOnUnknownAlias() {
        createYamlReader("""
        |  - &array a
        |  - *unknown
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("a")
            assertInvalidYaml()
        }
    }

    @Test
    fun onlyAnchor() {
        createYamlReader("""
        |  - &anchor
        """.trimMargin()).apply {
            assertStartArray()
            assertValue(null, ValueType.Null)
            assertEndArray()
            assertEndDocument()
        }
    }
}
