package maryk.core.json.yaml

import maryk.core.json.InvalidJsonContent
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForValue
import maryk.test.shouldThrow
import kotlin.test.Test

class StringInDoubleQuoteReaderTest {
    @Test
    fun read_double_quote() {
        val reader = createYamlReader(""""test"""")
        testForValue(reader, "test")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_double_quote_forgot_to_close() {
        val reader = createYamlReader(""""test""")
        shouldThrow<InvalidJsonContent> {
            testForValue(reader, "test")
        }
    }

    @Test
    fun read_double_quote_with_special_chars() {
        val reader = createYamlReader(""""te\"\b\f\n\t\\\/\r'"""")
        testForValue(reader, "te\"\b\u000C\n\t\\/\r'")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_double_quote_with_utf16_chars() {
        val reader = createYamlReader(""""\uD83D\uDE0D\uwrong\u0w\u00w\u000w"""")
        testForValue(reader, "😍\\uwrong\\u0w\\u00w\\u000w")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_double_quote_with_utf8_chars() {
        val reader = createYamlReader(""""\x43\x52\xw0\x0w"""")
        testForValue(reader, "\u0043\u0052\\xw0\\x0w")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_double_quote_with_utf32_chars() {
        val reader = createYamlReader(""""\U0001F603\U0001W603"""")
        testForValue(reader, "\uD83D\uDE03\\U0001W603")
        testForDocumentEnd(reader)
    }
}