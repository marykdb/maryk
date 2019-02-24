package maryk.json

import maryk.test.shouldBe
import kotlin.test.fail

fun IsJsonLikeReader.assertStartObject(type: MapType = MapType.Map) {
    this.nextToken().apply {
        if (this is JsonToken.StartObject) {
            type.let {
                this.type shouldBe it
            }
        } else {
            fail("$this should be object start")
        }
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
        } else {
            fail("$this should be field name '$value'")
        }
    }
}

fun IsJsonLikeReader.assertStartArray(type: ArrayType = ArrayType.Sequence) {
    this.nextToken().apply {
        if (this is JsonToken.StartArray) {
            type.let {
                this.type shouldBe it
            }
        } else {
            fail("$this should be array start")
        }
    }
}

fun IsJsonLikeReader.assertEndArray() {
    this.nextToken().apply {
        if (this !== JsonToken.EndArray) {
            fail("$this should be array end")
        }
    }
}

fun <T : Any> IsJsonLikeReader.assertValue(value: T?, type: ValueType<T>? = null) {
    this.nextToken().apply {
        if (this is JsonToken.Value<*>) {
            this.value shouldBe value

            type?.let {
                this.type shouldBe it
            }
        } else {
            fail("$this should be value '$value'")
        }
    }
}

fun IsJsonLikeReader.assertEndDocument() {
    this.nextToken().apply {
        if (this !== JsonToken.EndDocument) {
            fail("$this should be End Document")
        }
    }
}
