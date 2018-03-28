package maryk.core.json.yaml

import maryk.core.json.assertEndDocument
import maryk.core.json.assertInvalidYaml
import maryk.core.json.assertValue
import kotlin.test.Test

class StringInDoubleQuoteReaderTest {
    @Test
    fun read_double_quote() {
        createYamlReader(""""test"""").apply {
            assertValue("test")
            assertEndDocument()
        }
    }

    @Test
    fun read_double_quote_forgot_to_close() {
        createYamlReader(""""test""").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun read_double_quote_with_special_chars() {
        createYamlReader(""""te\"\b\f\n\t\\\/\r'"""").apply {
            assertValue("te\"\b\u000C\n\t\\/\r'")
            assertEndDocument()
        }
    }

    @Test
    fun read_double_quote_with_utf16_chars() {
        createYamlReader(""""\uD83D\uDE0D\uwrong\u0w\u00w\u000w"""").apply {
            assertValue("😍\\uwrong\\u0w\\u00w\\u000w")
            assertEndDocument()
        }
    }

    @Test
    fun read_double_quote_with_utf8_chars() {
        createYamlReader(""""\x43\x52\xw0\x0w"""").apply {
            assertValue("\u0043\u0052\\xw0\\x0w")
            assertEndDocument()
        }
    }

    @Test
    fun read_double_quote_with_utf32_chars() {
        createYamlReader(""""\U0001F603\U0001W603"""").apply {
            assertValue("\uD83D\uDE03\\U0001W603")
            assertEndDocument()
        }
    }
}