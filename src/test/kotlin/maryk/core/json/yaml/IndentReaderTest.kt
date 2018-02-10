package maryk.core.json.yaml

import maryk.core.json.testForEndJson
import maryk.core.json.testForObjectValue
import kotlin.test.Test

class IndentReaderTest {
    @Test
    fun read_single_quotes_with_indent() {
        val reader = createYamlReader("""
            |     'test'
        """.trimMargin())
        testForObjectValue(reader, "test")
        testForEndJson(reader)
    }
}