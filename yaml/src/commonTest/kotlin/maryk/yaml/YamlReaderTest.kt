package maryk.yaml

import maryk.json.IsJsonLikeReader
import maryk.json.JsonToken
import maryk.json.ValueType
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

        createSimpleYamlReader(input).apply {
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
    fun read_exception() {
        createYamlReader("key:\n `wrong").apply {
            assertStartObject()
            assertFieldName("key")
            val a = assertInvalidYaml()
            a.columnNumber shouldBe 1
            a.lineNumber shouldBe 2
            this.columnNumber shouldBe 1
            this.lineNumber shouldBe 2
        }
    }

    @Test
    fun read_prefix_tag() {
        val reader = createYamlReader("%TAG !prefix! !B\n---") as YamlReaderImpl
        reader.nextToken()
        reader.resolveTag("!prefix!", "ar") shouldBe TestType.Bar
    }

    @Test
    fun fail_on_invalid_URI_tag() {
        val reader = createYamlReader("") as YamlReaderImpl
        shouldThrow<InvalidYamlContent> {
            reader.resolveTag("!", "<wrong>")
        }
    }

    @Test
    fun fail_on_unknown_URI_tag() {
        shouldThrow<InvalidYamlContent> {
            (createYamlReader("") as YamlReaderImpl)
                .resolveTag("!", "<tag:unknown.org,2002>")
        }
    }

    @Test
    fun fail_on_unknown_tag() {
        shouldThrow<InvalidYamlContent> {
            (createYamlReader("") as YamlReaderImpl)
                .resolveTag("!", "unknown")
        }
    }

    @Test
    fun fail_on_unknown_default_tag() {
        shouldThrow<InvalidYamlContent> {
            (createYamlReader("") as YamlReaderImpl)
                .resolveTag("!!", "unknown")
        }
    }

    @Test
    fun fail_on_unknown_named_tag() {
        shouldThrow<InvalidYamlContent> {
            (createYamlReader("%TAG !known! tag:unknown.org,2002\n---") as YamlReaderImpl).let {
                it.nextToken()
                it.resolveTag("!known!", "unknown")
            }
        }
    }
}
