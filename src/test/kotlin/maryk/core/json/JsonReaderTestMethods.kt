package maryk.core.json

import maryk.core.json.yaml.YamlReader
import maryk.test.shouldBe
import kotlin.test.fail

internal fun testForObjectValue(reader: YamlReader, value: String) {
    reader.nextToken().apply {
        if (this is JsonToken.ObjectValue) {
            this.value shouldBe value
        } else { fail("$this should be object value") }
    }
}

internal fun testForArrayValue(reader: YamlReader, value: String) {
    reader.nextToken().apply {
        if (this is JsonToken.ArrayValue) {
            this.value shouldBe value
        } else { fail("$this should be object value") }
    }
}

internal fun testForEndJson(reader: YamlReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.EndJSON) {
            fail("$this should be End JSON")
        }
    }
}