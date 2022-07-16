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
    expect(type) { assertIs<StartObject>(this.nextToken()).type }
}

fun IsJsonLikeReader.assertEndObject() {
    expect(EndObject) { this.nextToken() }
}

fun IsJsonLikeReader.assertFieldName(value: String?) {
    expect(value) { assertIs<FieldName>(this.nextToken()).value }
}

fun IsJsonLikeReader.assertStartArray(type: ArrayType = ArrayType.Sequence) {
    expect(type) { assertIs<StartArray>(this.nextToken()).type }
}

fun IsJsonLikeReader.assertEndArray() {
    expect(EndArray) { this.nextToken() }
}

fun <T : Any> IsJsonLikeReader.assertValue(value: T?, type: ValueType<T>? = null) {
    assertIs<Value<*>>(this.nextToken()).apply {
        expect(value) { this.value }
        type?.let {
            expect(type) { this.type }
        }
    }
}

fun IsJsonLikeReader.assertEndDocument() {
    expect(EndDocument) { this.nextToken() }
}
