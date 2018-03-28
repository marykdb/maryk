package maryk.core.json.yaml

import maryk.core.json.assertEndDocument
import maryk.core.json.assertInvalidYaml
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

    @Test
    fun fail_not_closed_single_quote() {
        createYamlReader("'test").apply {
            assertInvalidYaml()
        }
    }
}
