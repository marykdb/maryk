package maryk.core.json.yaml

import maryk.core.json.assertEndDocument
import maryk.core.json.assertValue
import kotlin.test.Test

class IndentReaderTest {
    @Test
    fun read_single_quotes_with_indent() {
        createYamlReader("""
            |     'test'
        """.trimMargin()).apply {
            assertValue("test")
            assertEndDocument()
        }
    }
}