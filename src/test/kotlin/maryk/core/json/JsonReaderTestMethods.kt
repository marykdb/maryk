package maryk.core.json

import maryk.test.shouldBe
import kotlin.test.fail

internal fun testForObjectValue(reader: IsJsonLikeReader, value: String) {
    reader.nextToken().apply {
        if (this is JsonToken.ObjectValue) {
            this.value shouldBe value
        } else { fail("$this should be object value") }
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
        } else { fail("$this should be object value") }
    }
}

internal fun testForEndJson(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.EndJSON) {
            fail("$this should be End JSON")
        }
    }
}