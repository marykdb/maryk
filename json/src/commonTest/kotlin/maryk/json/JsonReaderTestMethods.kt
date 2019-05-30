package maryk.json

import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.EndDocument
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Value
import maryk.test.assertType
import kotlin.test.expect

fun IsJsonLikeReader.assertStartObject(type: MapType = MapType.Map) {
    expect(type) { assertType<StartObject>(this.nextToken()).type }
}

fun IsJsonLikeReader.assertEndObject() {
    expect(EndObject) { this.nextToken() }
}

fun IsJsonLikeReader.assertFieldName(value: String?) {
    expect(value) { assertType<FieldName>(this.nextToken()).value }
}

fun IsJsonLikeReader.assertStartArray(type: ArrayType = ArrayType.Sequence) {
    expect(type) { assertType<StartArray>(this.nextToken()).type }
}

fun IsJsonLikeReader.assertEndArray() {
    expect(EndArray) { this.nextToken() }
}

fun <T : Any> IsJsonLikeReader.assertValue(value: T?, type: ValueType<T>? = null) {
    assertType<Value<*>>(this.nextToken()).apply {
        expect(value) { this.value }
        type?.let {
            expect(type) { this.type }
        }
    }
}

fun IsJsonLikeReader.assertEndDocument() {
    expect(EndDocument) { this.nextToken() }
}
