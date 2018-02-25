package maryk.core.json

import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.fail

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

internal fun testForObjectValue(reader: IsJsonLikeReader, value: String?) {
    reader.nextToken().apply {
        if (this is JsonToken.ObjectValue) {
            this.value shouldBe value
        } else { fail("$this should be object value '$value'") }
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

internal fun testForArrayValue(reader: IsJsonLikeReader, value: String) {
    reader.nextToken().apply {
        if (this is JsonToken.ArrayValue) {
            this.value shouldBe value
        } else { fail("$this should be Array value '$value'") }
    }
}

internal fun testForEndJson(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.EndJSON) {
            fail("$this should be End JSON")
        }
    }
}

internal fun testForInvalidJson(reader: IsJsonLikeReader) {
    shouldThrow<InvalidJsonContent> {
        println(reader.nextToken())
    }
}