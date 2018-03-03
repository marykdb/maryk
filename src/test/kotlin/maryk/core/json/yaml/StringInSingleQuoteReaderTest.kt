package maryk.core.json.yaml

import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForObjectValue
import kotlin.test.Test

class StringInSingleQuoteReaderTest {
    @Test
    fun read_single_quote() {
        val reader = createYamlReader("'te''st\"'")
        testForObjectValue(reader, "te'st\"")
        testForDocumentEnd(reader)
    }
}