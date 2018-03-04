package maryk.core.json

import maryk.core.json.yaml.InvalidYamlContent
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.fail

internal fun testForDocumentStart(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.StartDocument) {
            fail("$this should be document start")
        }
    }
}

internal fun testForObjectStart(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.StartObject) {
            fail("$this should be object start")
        }
    }
}

internal fun testForObjectEnd(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.EndObject) {
            fail("$this should be object end")
        }
    }
}

internal fun testForFieldName(reader: IsJsonLikeReader, value: String?) {
    reader.nextToken().apply {
        if (this is JsonToken.FieldName) {
            this.value shouldBe value
        } else { fail("$this should be field name '$value'") }
    }
}

internal fun testForArrayStart(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.StartArray) {
            fail("$this should be array start")
        }
    }
}

internal fun testForArrayEnd(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.EndArray) {
            fail("$this should be array end")
        }
    }
}

internal fun testForValue(reader: IsJsonLikeReader, value: Any?) {
    reader.nextToken().apply {
        if (this is JsonToken.Value<*>) {
            this.value shouldBe value
        } else { fail("$this should be value '$value'") }
    }
}

internal fun testForDocumentEnd(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.EndDocument) {
            fail("$this should be End Document")
        }
    }
}

internal fun testForInvalidJson(reader: IsJsonLikeReader) {
    shouldThrow<InvalidJsonContent> {
        println(reader.nextToken())
    }
}

internal fun testForInvalidYaml(reader: IsJsonLikeReader) {
    shouldThrow<InvalidYamlContent> {
        println(reader.nextToken())
    }
}