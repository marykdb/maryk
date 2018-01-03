package maryk.core.json.yaml

import maryk.core.json.AbstractJsonWriterTest
import maryk.test.shouldBe
import kotlin.test.Test

private const val YAML_OUTPUT = """- 1
- "#Test"
- 3.5
- true
- test: false
  test2: value
- another: yes
"""

internal class YamlWriterTest : AbstractJsonWriterTest() {
    override fun createJsonWriter(writer: (String) -> Unit) = YamlWriter(writer = writer)

    @Test
    fun `write expected YAML`() {
        var output = ""
        val writer = { string: String -> output += string }

        val generator = YamlWriter(writer = writer)

        writeJson(generator)

        output shouldBe YAML_OUTPUT
    }
}