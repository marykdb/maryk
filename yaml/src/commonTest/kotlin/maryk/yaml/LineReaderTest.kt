package maryk.yaml

import kotlin.test.Test

class LineReaderTest {
    @Test
    fun failOnReservedIndicator() {
        createYamlReader("  @").apply {
            assertInvalidYaml()
        }

        createYamlReader("  `").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnDirective() {
        createYamlReader("  %test").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnEndMap() {
        createYamlReader("  ]").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnComma() {
        createYamlReader("  ,").apply {
            assertInvalidYaml()
        }
    }
}
