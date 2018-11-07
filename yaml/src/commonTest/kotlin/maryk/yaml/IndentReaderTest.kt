package maryk.yaml

import kotlin.test.Test

class IndentReaderTest {
    @Test
    fun readSingleQuotesWithIndent() {
        createYamlReader("""
            |     'test'
        """.trimMargin()).apply {
            assertValue("test")
            assertEndDocument()
        }
    }
}
