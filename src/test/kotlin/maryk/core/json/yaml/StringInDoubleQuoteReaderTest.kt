package maryk.core.json.yaml

import maryk.core.json.InvalidJsonContent
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForObjectValue
import maryk.test.shouldThrow
import kotlin.test.Test

class StringInDoubleQuoteReaderTest {
    @Test
    fun read_double_quote() {
        val reader = createYamlReader(""""test"""")
        testForObjectValue(reader, "test")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_double_quote_forgot_to_close() {
        val reader = createYamlReader(""""test""")
        shouldThrow<InvalidJsonContent> {
            testForObjectValue(reader, "test")
        }
    }

    @Test
    fun read_double_quote_with_special_chars() {
        val reader = createYamlReader(""""te\"\b\f\n\t\\\/\r'"""")
        testForObjectValue(reader, "te\"\b\u000C\n\t\\/\r'")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_double_quote_with_utf16_chars() {
        val reader = createYamlReader(""""\uD83D\uDE0D\uwrong\u0w\u00w\u000w"""")
        testForObjectValue(reader, "😍\\uwrong\\u0w\\u00w\\u000w")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_double_quote_with_utf8_chars() {
        val reader = createYamlReader(""""\x43\x52\xw0\x0w"""")
        testForObjectValue(reader, "\u0043\u0052\\xw0\\x0w")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_double_quote_with_utf32_chars() {
        val reader = createYamlReader(""""\U0001F603\U0001W603"""")
        testForObjectValue(reader, "\uD83D\uDE03\\U0001W603")
        testForDocumentEnd(reader)
    }
}