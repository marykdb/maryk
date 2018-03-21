package maryk.core.json.yaml

import maryk.core.json.IsJsonLikeReader
import maryk.core.json.JsonToken
import maryk.core.json.ValueType
import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForFieldName
import maryk.core.json.testForObjectEnd
import maryk.core.json.testForObjectStart
import maryk.core.json.testForValue
import maryk.test.shouldBe
import kotlin.test.Test

class YamlReaderTest {
    @Test
    fun test_skip_fields_structure() {
        val input = """
        |  1: 567
        |  2: [a1, a2, a3]
        |  3:
        |      test1: 1
        |      test2: 2
        |      array: []
        |  4: v4
        |  5:
        |      map: {}
        |  6: v6
        |  7:
        |      seq:
        |      - a
        |      - b
        |  8: v8
        """.trimMargin()

        val reader = createYamlReader(input)
        testForObjectStart(reader)

        testForFieldName(reader, "1")
        reader.skipUntilNextField()

        testForCurrentField(reader, "2")
        reader.skipUntilNextField()

        testForCurrentField(reader, "3")
        reader.skipUntilNextField()

        testForCurrentField(reader, "4")
        testForValue(reader, "v4", ValueType.String)

        testForFieldName(reader, "5")
        reader.skipUntilNextField()

        testForCurrentField(reader, "6")
        testForValue(reader, "v6", ValueType.String)

        testForFieldName(reader, "7")
        reader.skipUntilNextField()

        testForCurrentField(reader, "8")
        testForValue(reader, "v8", ValueType.String)

        testForObjectEnd(reader)
        testForDocumentEnd(reader)
    }

    private fun testForCurrentField(reader: IsJsonLikeReader, value: String) {
        reader.currentToken.apply {
            (this is JsonToken.FieldName) shouldBe true
            (this as JsonToken.FieldName).value shouldBe value
        }
    }
}
