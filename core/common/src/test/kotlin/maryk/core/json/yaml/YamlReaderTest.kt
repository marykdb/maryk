package maryk.core.json.yaml

import maryk.core.json.IsJsonLikeReader
import maryk.core.json.JsonToken
import maryk.core.json.ValueType
import maryk.core.json.assertEndDocument
import maryk.core.json.assertEndObject
import maryk.core.json.assertFieldName
import maryk.core.json.assertStartObject
import maryk.core.json.assertValue
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class YamlReaderTest {
    @Test
    fun test_skip_fields_structure() {
        val input = """
        |  1: 567
        |  2: [a1, a2, a3]
        |  3:
        |      test1: 1
        |      test2: 2
        |      array: []
        |  4: v4
        |  5:
        |      map: {}
        |  6: v6
        |  7:
        |      seq:
        |      - a
        |      - b
        |  8: v8
        """.trimMargin()

        createYamlReader(input).apply {
            assertStartObject()

            assertFieldName("1")
            skipUntilNextField()

            assertCurrentFieldName("2")
            skipUntilNextField()

            assertCurrentFieldName("3")
            skipUntilNextField()

            assertCurrentFieldName("4")
            assertValue("v4", ValueType.String)

            assertFieldName("5")
            skipUntilNextField()

            assertCurrentFieldName("6")
            assertValue("v6", ValueType.String)

            assertFieldName("7")
            skipUntilNextField()

            assertCurrentFieldName("8")
            assertValue("v8", ValueType.String)

            assertEndObject()
            assertEndDocument()
        }
    }

    private fun IsJsonLikeReader.assertCurrentFieldName(value: String) {
        this.currentToken.apply {
            (this is JsonToken.FieldName) shouldBe true
            (this as JsonToken.FieldName).value shouldBe value
        }
    }

    @Test
    fun fail_on_invalid_URI_tag() {
        val reader = createYamlReader("") as YamlReaderImpl
        shouldThrow<InvalidYamlContent> {
            reader.resolveTag("!", "<wrong>")
        }
    }
}
