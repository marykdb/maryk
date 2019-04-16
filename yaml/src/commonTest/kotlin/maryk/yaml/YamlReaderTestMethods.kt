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
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import maryk.test.shouldThrow
import kotlin.test.fail

fun IsJsonLikeReader.assertStartDocument() {
    this.nextToken() shouldBe StartDocument
}

fun IsJsonLikeReader.assertStartObject(type: MapType = MapType.Map) {
    shouldBeOfType<StartObject>(this.nextToken()).apply {
        type.let {
            this.type shouldBe it
        }
    }
}

fun IsJsonLikeReader.assertEndObject() {
    this.nextToken() shouldBe EndObject
}

fun IsJsonLikeReader.assertFieldName(value: String?) {
    shouldBeOfType<FieldName>(this.nextToken()).value shouldBe value
}

fun IsJsonLikeReader.assertStartComplexFieldName() {
    this.nextToken() shouldBe StartComplexFieldName
}

fun IsJsonLikeReader.assertEndComplexFieldName() {
    this.nextToken() shouldBe EndComplexFieldName
}

fun IsJsonLikeReader.assertStartArray(type: ArrayType = ArrayType.Sequence) {
    shouldBeOfType<StartArray>(this.nextToken()).apply {
        type.let {
            this.type shouldBe it
        }
    }
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

fun IsJsonLikeReader.assertByteArrayValue(value: ByteArray, type: ValueType<ByteArray>) {
    this.nextToken().apply {
        if (this is Value<*> && this.value is ByteArray) {
            val byteArray = this.value as? ByteArray

            byteArray?.let {
                it.toHex() shouldBe value.toHex()
            } ?: fail("$this should be bytearray")

            type.let {
                this.type shouldBe it
            }
        } else {
            fail("$this should be value '$value'")
        }
    }
}

fun IsJsonLikeReader.assertEndDocument() {
    this.nextToken() shouldBe EndDocument
}

fun IsJsonLikeReader.assertInvalidYaml(): InvalidYamlContent =
    shouldThrow {
        println(this.nextToken())
    }
