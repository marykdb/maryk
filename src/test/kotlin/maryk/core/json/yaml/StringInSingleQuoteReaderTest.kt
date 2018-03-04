package maryk.core.json.yaml

import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForValue
import kotlin.test.Test

class StringInSingleQuoteReaderTest {
    @Test
    fun read_single_quote() {
        val reader = createYamlReader("'te''st\"'")
        testForValue(reader, "te'st\"")
        testForDocumentEnd(reader)
    }
}