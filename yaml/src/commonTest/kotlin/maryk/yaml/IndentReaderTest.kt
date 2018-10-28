package maryk.yaml

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
