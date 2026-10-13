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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.expect
import kotlin.test.fail

fun IsJsonLikeReader.assertStartDocument() {
    expect(StartDocument) { nextToken() }
}

fun IsJsonLikeReader.assertStartObject(type: MapType = MapType.Map) {
    assertIs<StartObject>(nextToken()).apply {
        assertEquals(type, this.type)
    }
}

fun IsJsonLikeReader.assertEndObject() {
    expect(EndObject) { nextToken() }
}

fun IsJsonLikeReader.assertFieldName(value: String?) {
    expect(value) {
        assertIs<FieldName>(nextToken()).value
    }
}

fun IsJsonLikeReader.assertStartComplexFieldName() {
    expect(StartComplexFieldName) { nextToken() }
}

fun IsJsonLikeReader.assertEndComplexFieldName() {
    expect(EndComplexFieldName) { nextToken() }
}

fun IsJsonLikeReader.assertStartArray(type: ArrayType = ArrayType.Sequence) {
    assertIs<StartArray>(nextToken()).apply {
        assertEquals(type, this.type)
    }
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

fun IsJsonLikeReader.assertByteArrayValue(value: ByteArray, type: ValueType<ByteArray>) {
    nextToken().apply {
        if (this is Value<*> && this.value is ByteArray) {
            val byteArray = this.value as? ByteArray

            byteArray?.let {
                expect(value.toHexString()) { it.toHexString() }
            } ?: fail("$this should be bytearray")

            assertEquals(type, this.type)
        } else {
            fail("$this should be value '$value'")
        }
    }
}

fun IsJsonLikeReader.assertEndDocument() {
    expect(EndDocument) { nextToken() }
}

fun IsJsonLikeReader.assertInvalidYaml(): InvalidYamlContent =
    assertFailsWith {
        println(nextToken())
    }
