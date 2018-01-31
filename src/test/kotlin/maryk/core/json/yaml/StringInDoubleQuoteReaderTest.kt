package maryk.core.json.yaml

import maryk.core.json.InvalidJsonContent
import maryk.core.json.testForEndJson
import maryk.core.json.testForObjectValue
import maryk.test.shouldThrow
import kotlin.test.Test

class StringInDoubleQuoteReaderTest {
    @Test
    fun read_double_quote() {
        val reader = createYamlReader(""""test"""")
        testForObjectValue(reader, "test")
        testForEndJson(reader)
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
        testForEndJson(reader)
    }

    @Test
    fun read_double_quote_with_utf_chars() {
        val reader = createYamlReader(""""\uD83D\uDE0D\uwrong\u0w\u00w\u000w"""")
        testForObjectValue(reader, "😍\\uwrong\\u0w\\u00w\\u000w")
        testForEndJson(reader)
    }
}