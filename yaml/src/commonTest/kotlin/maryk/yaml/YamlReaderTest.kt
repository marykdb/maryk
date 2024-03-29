package maryk.yaml

import maryk.json.IsJsonLikeReader
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.ValueType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.expect

class YamlReaderTest {
    @Test
    fun testSkipFieldsStructure() {
        val input = """
          1: 567
          2: [a1, a2, a3]
          3:
              test1: 1
              test2: 2
              array: []
          4: v4
          5:
              map: {}
          6: v6
          7:
              seq:
              - a
              - b
          8: v8
          9: [v9, v10]
        """.trimIndent()

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

            assertFieldName("9")
            skipUntilNextField()

            expect(EndObject) { this.currentToken }
            assertEndDocument()
        }
    }

    private fun IsJsonLikeReader.assertCurrentFieldName(value: String) {
        this.currentToken.apply {
            expect(value) { assertIs<FieldName>(this).value }
        }
    }

    @Test
    fun readException() {
        createYamlReader("key:\n `wrong").apply {
            assertStartObject()
            assertFieldName("key")
            val a = assertInvalidYaml()
            expect(1) { a.columnNumber }
            expect(2) { a.lineNumber }
            expect(1) { this.columnNumber }
            expect(2) { this.lineNumber }
        }
    }

    @Test
    fun readPrefixTag() {
        val reader = createYamlReader("%TAG !prefix! !B\n---") as YamlReaderImpl
        reader.nextToken()
        expect(TestType.Bar) { reader.resolveTag("!prefix!", "ar") }
    }

    @Test
    fun failOnInvalidURITag() {
        val reader = createYamlReader("") as YamlReaderImpl
        assertFailsWith<InvalidYamlContent> {
            reader.resolveTag("!", "<wrong>")
        }
    }

    @Test
    fun failOnUnknownURITag() {
        assertFailsWith<InvalidYamlContent> {
            (createYamlReader("") as YamlReaderImpl)
                .resolveTag("!", "<tag:unknown.org,2002>")
        }
    }

    @Test
    fun failOnUnknownTag() {
        assertFailsWith<InvalidYamlContent> {
            (createYamlReader("") as YamlReaderImpl)
                .resolveTag("!", "unknown")
        }
    }

    @Test
    fun failOnUnknownDefaultTag() {
        assertFailsWith<InvalidYamlContent> {
            (createYamlReader("") as YamlReaderImpl)
                .resolveTag("!!", "unknown")
        }
    }

    @Test
    fun failOnUnknownNamedTag() {
        assertFailsWith<InvalidYamlContent> {
            (createYamlReader("%TAG !known! tag:unknown.org,2002\n---") as YamlReaderImpl).let {
                it.nextToken()
                it.resolveTag("!known!", "unknown")
            }
        }
    }
}
