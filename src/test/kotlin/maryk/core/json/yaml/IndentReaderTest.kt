package maryk.core.json.yaml

import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForValue
import kotlin.test.Test

class IndentReaderTest {
    @Test
    fun read_single_quotes_with_indent() {
        val reader = createYamlReader("""
            |     'test'
        """.trimMargin())
        testForValue(reader, "test")
        testForDocumentEnd(reader)
    }
}