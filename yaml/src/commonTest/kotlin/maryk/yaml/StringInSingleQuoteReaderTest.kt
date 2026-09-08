package maryk.yaml

import kotlin.test.Test

class StringInSingleQuoteReaderTest {
    @Test
    fun normalizesRawLineBreaks() {
        listOf("\n", "\r\n", "\r").forEach { lineBreak ->
            createYamlReader("'first${lineBreak}second'").apply {
                assertValue("first\nsecond")
                assertEndDocument()
            }
        }
    }

    @Test
    fun readLongSingleQuotedString() {
        val value = "a".repeat(100_000)

        createYamlReader("'$value'").apply {
            assertValue(value)
            assertEndDocument()
        }
    }

    @Test
    fun readSingleQuote() {
        createYamlReader("'te''st\"'").apply {
            assertValue("te'st\"")
            assertEndDocument()
        }
    }

    @Test
    fun failNotClosedSingleQuote() {
        createYamlReader("'test").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun rejectsRawControlCharactersAndUnpairedSurrogates() {
        listOf("'value\u0001'", "'\uD800'").forEach { input ->
            createYamlReader(input).assertInvalidYaml()
        }
    }
}
