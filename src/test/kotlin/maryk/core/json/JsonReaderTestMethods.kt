package maryk.core.json

import maryk.core.extensions.toHex
import maryk.core.json.yaml.InvalidYamlContent
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.fail

internal fun testForDocumentStart(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !== JsonToken.StartDocument) {
            fail("$this should be document start")
        }
    }
}

internal fun testForObjectStart(reader: IsJsonLikeReader, type: MapType = MapType.Map) {
    reader.nextToken().apply {
        if (this is JsonToken.StartObject) {
            type.let {
                this.type shouldBe it
            }
        } else { fail("$this should be object start") }
    }
}

internal fun testForObjectEnd(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !== JsonToken.EndObject) {
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

internal fun testForComplexFieldNameStart(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.StartComplexFieldName) {
            fail("$this should be complex field name start")
        }
    }
}

internal fun testForComplexFieldNameEnd(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.EndComplexFieldName) {
            fail("$this should be complex field name end")
        }
    }
}

internal fun testForArrayStart(reader: IsJsonLikeReader, type: ArrayType = ArrayType.Sequence) {
    reader.nextToken().apply {
        if (this is JsonToken.StartArray) {
            type.let {
                this.type shouldBe it
            }
        } else { fail("$this should be array start") }
    }
}

internal fun testForArrayEnd(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !== JsonToken.EndArray) {
            fail("$this should be array end")
        }
    }
}

internal fun <T: Any> testForValue(reader: IsJsonLikeReader, value: T?, type: ValueType<T>? = null) {
    reader.nextToken().apply {
        if (this is JsonToken.Value<*>) {
            this.value shouldBe value

            type?.let {
                this.type shouldBe it
            }
        } else { fail("$this should be value '$value'") }
    }
}

internal fun testForByteArrayValue(reader: IsJsonLikeReader, value: ByteArray, type: ValueType<ByteArray>) {
    reader.nextToken().apply {
        if (this is JsonToken.Value<*> && this.value is ByteArray) {
            (this.value as ByteArray).toHex() shouldBe value.toHex()

            type.let {
                this.type shouldBe it
            }
        } else { fail("$this should be value '$value'") }
    }
}

internal fun testForDocumentEnd(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !== JsonToken.EndDocument) {
            fail("$this should be End Document")
        }
    }
}

internal fun testForInvalidYaml(reader: IsJsonLikeReader) {
    shouldThrow<InvalidYamlContent> {
        println(reader.nextToken())
    }
}
