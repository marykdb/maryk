package maryk.core.json.yaml

import maryk.core.json.assertEndDocument
import maryk.core.json.assertValue
import kotlin.test.Test

class StringInSingleQuoteReaderTest {
    @Test
    fun read_single_quote() {
        createYamlReader("'te''st\"'").apply {
            assertValue("te'st\"")
            assertEndDocument()
        }
    }
}