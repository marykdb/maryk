package maryk.core.json.yaml

import maryk.core.json.assertInvalidYaml
import kotlin.test.Test

class LineReaderTest {
    @Test
    fun fail_on_reserved_indicator() {
        createYamlReader("  @").apply {
            assertInvalidYaml()
        }

        createYamlReader("  `").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_directive() {
        createYamlReader("  %test").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_end_map() {
        createYamlReader("  ]").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun fail_on_comma() {
        createYamlReader("  ,").apply {
            assertInvalidYaml()
        }
    }
}
