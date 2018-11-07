package maryk.yaml

import kotlin.test.Test

class StringInSingleQuoteReaderTest {
    @Test
    fun readSingleQuote() {
        createYamlReader("'te''st\"'").apply {
            assertValue("te'st\"")
            assertEndDocument()
        }
    }

    @Test
    fun failNotClosedSingleQuote() {
        createYamlReader("'test").apply {
            assertInvalidYaml()
        }
    }
}
