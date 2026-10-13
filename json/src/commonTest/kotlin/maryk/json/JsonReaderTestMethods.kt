package maryk.json

import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.EndDocument
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Value
import kotlin.test.assertIs
import kotlin.test.expect

fun IsJsonLikeReader.assertStartObject(type: MapType = MapType.Map) {
    expect(type) { assertIs<StartObject>(nextToken()).type }
}

fun IsJsonLikeReader.assertEndObject() {
    expect(EndObject) { nextToken() }
}

fun IsJsonLikeReader.assertFieldName(value: String?) {
    expect(value) { assertIs<FieldName>(nextToken()).value }
}

fun IsJsonLikeReader.assertStartArray(type: ArrayType = ArrayType.Sequence) {
    expect(type) { assertIs<StartArray>(nextToken()).type }
}

fun IsJsonLikeReader.assertEndArray() {
    expect(EndArray) { nextToken() }
}

fun <T : Any> IsJsonLikeReader.assertValue(value: T?, type: ValueType<T>? = null) {
    assertIs<Value<*>>(nextToken()).apply {
        expect(value) { this.value }
        type?.let {
            expect(type) { this.type }
        }
    }
}

fun IsJsonLikeReader.assertEndDocument() {
    expect(EndDocument) { nextToken() }
}
