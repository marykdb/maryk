package maryk.yaml

import maryk.json.IsJsonLikeReader
import maryk.json.JsonToken.FieldName
import maryk.json.ValueType
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import maryk.test.shouldThrow
import kotlin.test.Test

class YamlReaderTest {
    @Test
    fun testSkipFieldsStructure() {
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
            shouldBeOfType<FieldName>(this).value shouldBe value
        }
    }

    @Test
    fun readException() {
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
    fun readPrefixTag() {
        val reader = createYamlReader("%TAG !prefix! !B\n---") as YamlReaderImpl
        reader.nextToken()
        reader.resolveTag("!prefix!", "ar") shouldBe TestType.Bar
    }

    @Test
    fun failOnInvalidURITag() {
        val reader = createYamlReader("") as YamlReaderImpl
        shouldThrow<InvalidYamlContent> {
            reader.resolveTag("!", "<wrong>")
        }
    }

    @Test
    fun failOnUnknownURITag() {
        shouldThrow<InvalidYamlContent> {
            (createYamlReader("") as YamlReaderImpl)
                .resolveTag("!", "<tag:unknown.org,2002>")
        }
    }

    @Test
    fun failOnUnknownTag() {
        shouldThrow<InvalidYamlContent> {
            (createYamlReader("") as YamlReaderImpl)
                .resolveTag("!", "unknown")
        }
    }

    @Test
    fun failOnUnknownDefaultTag() {
        shouldThrow<InvalidYamlContent> {
            (createYamlReader("") as YamlReaderImpl)
                .resolveTag("!!", "unknown")
        }
    }

    @Test
    fun failOnUnknownNamedTag() {
        shouldThrow<InvalidYamlContent> {
            (createYamlReader("%TAG !known! tag:unknown.org,2002\n---") as YamlReaderImpl).let {
                it.nextToken()
                it.resolveTag("!known!", "unknown")
            }
        }
    }
}
