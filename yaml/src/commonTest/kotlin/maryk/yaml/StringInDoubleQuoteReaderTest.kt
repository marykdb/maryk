package maryk.yaml

import kotlin.test.Test

class StringInDoubleQuoteReaderTest {
    @Test
    fun readDoubleQuote() {
        createYamlReader(""""test"""").apply {
            assertValue("test")
            assertEndDocument()
        }
    }

    @Test
    fun readDoubleQuoteForgotToClose() {
        createYamlReader(""""test""").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun readDoubleQuoteWithSpecialChars() {
        createYamlReader(""""te\"\b\f\n\t\\\/\r'\0\a\v\e\ \N\_\L\P\G"""").apply {
            assertValue("te\"\b\u000C\n\t\\/\r'\u0000\u0007\u000B\u001B \u0085\u00A0\u2028\u2029\\G")
            assertEndDocument()
        }
    }

    @Test
    fun readDoubleQuoteWithUtf16Chars() {
        createYamlReader(""""\uD83D\uDE0D\uwrong\u0w\u00w\u000w"""").apply {
            assertValue("😍\\uwrong\\u0w\\u00w\\u000w")
            assertEndDocument()
        }
    }

    @Test
    fun readDoubleQuoteWithUtf8Chars() {
        createYamlReader(""""\x43\x52\xw0\x0w"""").apply {
            assertValue("\u0043\u0052\\xw0\\x0w")
            assertEndDocument()
        }
    }

    @Test
    fun readDoubleQuoteWithUtf32Chars() {
        createYamlReader(""""\U0001F603\U0001W603"""").apply {
            assertValue("\uD83D\uDE03\\U0001W603")
            assertEndDocument()
        }
    }
}
