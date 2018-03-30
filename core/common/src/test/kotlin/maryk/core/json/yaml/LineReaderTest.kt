package maryk.core.json.yaml

import maryk.core.json.assertInvalidYaml
import kotlin.test.Test

class LineReaderTest {
    @Test
    fun fail_on_reserver_indicator() {
        createYamlReader("  @").apply {
            assertInvalidYaml()
        }

        createYamlReader("  `").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_end_map() {
        createYamlReader("  ]").apply {
            assertInvalidYaml()
        }
    }
}
