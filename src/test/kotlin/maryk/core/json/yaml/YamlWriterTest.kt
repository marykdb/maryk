package maryk.core.json.yaml

import maryk.core.json.AbstractJsonWriterTest
import maryk.test.shouldBe
import kotlin.test.Test

private const val YAML_OUTPUT = """- 1
- '#Test'
- 3.5
- true
- test: false
  test2: value
- another: yes
"""

internal class YamlWriterTest : AbstractJsonWriterTest() {
    override fun createJsonWriter(writer: (String) -> Unit) = YamlWriter(writer = writer)

    @Test
    fun write_expected_YAML() {
        var output = ""
        val writer = { string: String -> output += string }
        val generator = YamlWriter(writer = writer)

        writeJson(generator)

        output shouldBe YAML_OUTPUT
    }

    @Test
    fun write_YAML_in_double_array() {
        var output = ""
        val writer = { string: String -> output += string }
        val generator = YamlWriter(writer = writer)

        generator.writeStartArray()
        generator.writeStartArray()
        generator.writeValue("1")
        generator.writeValue("2")
        generator.writeEndArray()
        generator.writeStartArray()
        generator.writeValue("3")
        generator.writeValue("4")
        generator.writeEndArray()
        generator.writeEndArray()

        output shouldBe """
        |-
        |  - 1
        |  - 2
        |-
        |  - 3
        |  - 4
        |""".trimMargin()
    }
}
