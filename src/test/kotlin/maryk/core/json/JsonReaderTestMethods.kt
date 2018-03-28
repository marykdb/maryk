package maryk.core.json

import maryk.core.extensions.toHex
import maryk.core.json.yaml.InvalidYamlContent
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.fail

fun IsJsonLikeReader.assertStartDocument() {
    this.nextToken().apply {
        if (this !== JsonToken.StartDocument) {
            fail("$this should be document start")
        }
    }
}

fun IsJsonLikeReader.assertStartObject(type: MapType = MapType.Map) {
    this.nextToken().apply {
        if (this is JsonToken.StartObject) {
            type.let {
                this.type shouldBe it
            }
        } else { fail("$this should be object start") }
    }
}

fun IsJsonLikeReader.assertEndObject() {
    this.nextToken().apply {
        if (this !== JsonToken.EndObject) {
            fail("$this should be object end")
        }
    }
}

fun IsJsonLikeReader.assertFieldName(value: String?) {
    this.nextToken().apply {
        if (this is JsonToken.FieldName) {
            this.value shouldBe value
        } else { fail("$this should be field name '$value'") }
    }
}

fun IsJsonLikeReader.assertStartComplexFieldName() {
    this.nextToken().apply {
        if (this !is JsonToken.StartComplexFieldName) {
            fail("$this should be complex field name start")
        }
    }
}

fun IsJsonLikeReader.assertEndComplexFieldName() {
    this.nextToken().apply {
        if (this !is JsonToken.EndComplexFieldName) {
            fail("$this should be complex field name end")
        }
    }
}

fun IsJsonLikeReader.assertStartArray(type: ArrayType = ArrayType.Sequence) {
    this.nextToken().apply {
        if (this is JsonToken.StartArray) {
            type.let {
                this.type shouldBe it
            }
        } else { fail("$this should be array start") }
    }
}

fun IsJsonLikeReader.assertEndArray() {
    this.nextToken().apply {
        if (this !== JsonToken.EndArray) {
            fail("$this should be array end")
        }
    }
}

fun <T: Any> IsJsonLikeReader.assertValue(value: T?, type: ValueType<T>? = null) {
    this.nextToken().apply {
        if (this is JsonToken.Value<*>) {
            this.value shouldBe value

            type?.let {
                this.type shouldBe it
            }
        } else { fail("$this should be value '$value'") }
    }
}

fun IsJsonLikeReader.assertByteArrayValue(value: ByteArray, type: ValueType<ByteArray>) {
    this.nextToken().apply {
        if (this is JsonToken.Value<*> && this.value is ByteArray) {
            val byteArray = this.value as? ByteArray

            byteArray?.let {
                it.toHex() shouldBe value.toHex()
            } ?: fail("$this should be bytearray")

            type.let {
                this.type shouldBe it
            }
        } else { fail("$this should be value '$value'") }
    }
}

fun IsJsonLikeReader.assertEndDocument() {
    this.nextToken().apply {
        if (this !== JsonToken.EndDocument) {
            fail("$this should be End Document")
        }
    }
}

fun IsJsonLikeReader.assertInvalidYaml() {
    shouldThrow<InvalidYamlContent> {
        println(this.nextToken())
    }
}