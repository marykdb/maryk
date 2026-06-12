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
        createYamlReader(""""te\"\b\f\n\t\\\/\r'\0\a\v\e\ \N\_\L\P"""").apply {
            assertValue("te\"\b\u000C\n\t\\/\r'\u0000\u0007\u000B\u001B \u0085\u00A0\u2028\u2029")
            assertEndDocument()
        }
    }

    @Test
    fun rejectUnknownDoubleQuoteEscape() {
        createYamlReader(""""bad\G"""").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun readDoubleQuoteWithUtf16Chars() {
        createYamlReader(""""\uD83D\uDE0D"""").apply {
            assertValue("😍")
            assertEndDocument()
        }
    }

    @Test
    fun rejectInvalidUtf16Escape() {
        createYamlReader(""""\uwrong"""").apply {
            assertInvalidYaml()
        }
        createYamlReader(""""\u0w00"""").apply {
            assertInvalidYaml()
        }
        createYamlReader(""""\uD800"""").apply {
            assertInvalidYaml()
        }
        createYamlReader(""""\uDE0D"""").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun readDoubleQuoteWithUtf8Chars() {
        createYamlReader(""""\x43\x52"""").apply {
            assertValue("\u0043\u0052")
            assertEndDocument()
        }
    }

    @Test
    fun rejectInvalidUtf8Escape() {
        createYamlReader(""""\xw0"""").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun readDoubleQuoteWithUtf32Chars() {
        createYamlReader(""""\U0001F603"""").apply {
            assertValue("\uD83D\uDE03")
            assertEndDocument()
        }
    }

    @Test
    fun rejectInvalidUtf32Escape() {
        createYamlReader(""""\U0001W603"""").apply {
            assertInvalidYaml()
        }
        createYamlReader(""""\U0000D800"""").apply {
            assertInvalidYaml()
        }
        createYamlReader(""""\U00110000"""").apply {
            assertInvalidYaml()
        }
    }
}
