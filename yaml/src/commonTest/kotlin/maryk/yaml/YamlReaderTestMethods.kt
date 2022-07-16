package maryk.yaml

import maryk.json.ArrayType
import maryk.json.IsJsonLikeReader
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.EndComplexFieldName
import maryk.json.JsonToken.EndDocument
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartComplexFieldName
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Value
import maryk.json.MapType
import maryk.json.ValueType
import maryk.lib.extensions.toHex
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.expect
import kotlin.test.fail

fun IsJsonLikeReader.assertStartDocument() {
    expect(StartDocument) { this.nextToken() }
}

fun IsJsonLikeReader.assertStartObject(type: MapType = MapType.Map) {
    assertIs<StartObject>(this.nextToken()).apply {
        assertEquals(type, this.type)
    }
}

fun IsJsonLikeReader.assertEndObject() {
    expect(EndObject) { this.nextToken() }
}

fun IsJsonLikeReader.assertFieldName(value: String?) {
    expect(value) {
        assertIs<FieldName>(this.nextToken()).value
    }
}

fun IsJsonLikeReader.assertStartComplexFieldName() {
    expect(StartComplexFieldName) { this.nextToken() }
}

fun IsJsonLikeReader.assertEndComplexFieldName() {
    expect(EndComplexFieldName) { this.nextToken() }
}

fun IsJsonLikeReader.assertStartArray(type: ArrayType = ArrayType.Sequence) {
    assertIs<StartArray>(this.nextToken()).apply {
        assertEquals(type, this.type)
    }
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

fun IsJsonLikeReader.assertByteArrayValue(value: ByteArray, type: ValueType<ByteArray>) {
    this.nextToken().apply {
        if (this is Value<*> && this.value is ByteArray) {
            val byteArray = this.value as? ByteArray

            byteArray?.let {
                expect(value.toHex()) { it.toHex() }
            } ?: fail("$this should be bytearray")

            assertEquals(type, this.type)
        } else {
            fail("$this should be value '$value'")
        }
    }
}

fun IsJsonLikeReader.assertEndDocument() {
    expect(EndDocument) { this.nextToken() }
}

fun IsJsonLikeReader.assertInvalidYaml(): InvalidYamlContent =
    assertFailsWith {
        println(this.nextToken())
    }
