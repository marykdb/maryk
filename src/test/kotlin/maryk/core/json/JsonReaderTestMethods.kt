package maryk.core.json

import maryk.core.extensions.toHex
import maryk.core.json.yaml.InvalidYamlContent
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.fail

fun testForDocumentStart(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !== JsonToken.StartDocument) {
            fail("$this should be document start")
        }
    }
}

fun testForObjectStart(reader: IsJsonLikeReader, type: MapType = MapType.Map) {
    reader.nextToken().apply {
        if (this is JsonToken.StartObject) {
            type.let {
                this.type shouldBe it
            }
        } else { fail("$this should be object start") }
    }
}

fun testForObjectEnd(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !== JsonToken.EndObject) {
            fail("$this should be object end")
        }
    }
}

fun testForFieldName(reader: IsJsonLikeReader, value: String?) {
    reader.nextToken().apply {
        if (this is JsonToken.FieldName) {
            this.value shouldBe value
        } else { fail("$this should be field name '$value'") }
    }
}

fun testForComplexFieldNameStart(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.StartComplexFieldName) {
            fail("$this should be complex field name start")
        }
    }
}

fun testForComplexFieldNameEnd(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !is JsonToken.EndComplexFieldName) {
            fail("$this should be complex field name end")
        }
    }
}

fun testForArrayStart(reader: IsJsonLikeReader, type: ArrayType = ArrayType.Sequence) {
    reader.nextToken().apply {
        if (this is JsonToken.StartArray) {
            type.let {
                this.type shouldBe it
            }
        } else { fail("$this should be array start") }
    }
}

fun testForArrayEnd(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !== JsonToken.EndArray) {
            fail("$this should be array end")
        }
    }
}

fun <T: Any> testForValue(reader: IsJsonLikeReader, value: T?, type: ValueType<T>? = null) {
    reader.nextToken().apply {
        if (this is JsonToken.Value<*>) {
            this.value shouldBe value

            type?.let {
                this.type shouldBe it
            }
        } else { fail("$this should be value '$value'") }
    }
}

fun testForByteArrayValue(reader: IsJsonLikeReader, value: ByteArray, type: ValueType<ByteArray>) {
    reader.nextToken().apply {
        if (this is JsonToken.Value<*> && this.value is ByteArray) {
            (this.value as ByteArray).toHex() shouldBe value.toHex()

            type.let {
                this.type shouldBe it
            }
        } else { fail("$this should be value '$value'") }
    }
}

fun testForDocumentEnd(reader: IsJsonLikeReader) {
    reader.nextToken().apply {
        if (this !== JsonToken.EndDocument) {
            fail("$this should be End Document")
        }
    }
}

fun testForInvalidYaml(reader: IsJsonLikeReader) {
    shouldThrow<InvalidYamlContent> {
        println(reader.nextToken())
    }
}
