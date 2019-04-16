package maryk.json

import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.EndDocument
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Value
import maryk.test.shouldBe
import maryk.test.shouldBeOfType

fun IsJsonLikeReader.assertStartObject(type: MapType = MapType.Map) {
    shouldBeOfType<StartObject>(this.nextToken()).type shouldBe type
}

fun IsJsonLikeReader.assertEndObject() {
    this.nextToken() shouldBe EndObject
}

fun IsJsonLikeReader.assertFieldName(value: String?) {
    shouldBeOfType<FieldName>(this.nextToken()).value shouldBe value
}

fun IsJsonLikeReader.assertStartArray(type: ArrayType = ArrayType.Sequence) {
    shouldBeOfType<StartArray>(this.nextToken()).type shouldBe type
}

fun IsJsonLikeReader.assertEndArray() {
    this.nextToken() shouldBe EndArray
}

fun <T : Any> IsJsonLikeReader.assertValue(value: T?, type: ValueType<T>? = null) {
    shouldBeOfType<Value<*>>(this.nextToken()).apply {
        this.value shouldBe value
        type?.let {
            this.type shouldBe it
        }
    }
}

fun IsJsonLikeReader.assertEndDocument() {
    this.nextToken() shouldBe EndDocument
}
