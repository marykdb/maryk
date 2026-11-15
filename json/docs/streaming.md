# Streaming JSON API

Start with the [module overview](../README.md) for a first reader and writer.
This guide describes the low-level streaming API; Maryk model serializers are
covered by the [serialization guide](../../core/docs/serialization/json.md).

## Writer methods

[`JsonWriter`](../src/commonMain/kotlin/maryk/json/JsonWriter.kt) accepts
`pretty = false` by default and a `(String) -> Unit` output callback.

| Method | Purpose |
| --- | --- |
| `writeStartObject()` / `writeEndObject()` | Open or close an object |
| `writeStartArray()` / `writeEndArray()` | Open or close an array |
| `writeFieldName(name)` | Write an object field name and colon |
| `writeString(value)` | Escape and quote string content |
| `writeInt`, `writeLong` | Write integer values |
| `writeFloat`, `writeDouble` | Write finite floating-point values |
| `writeBoolean`, `writeNull` | Write a boolean or null |
| `writeValue(value)` | Write raw value text without quotes; the caller owns its validity |

For example, write an object to a string:

```kotlin
import maryk.json.JsonWriter

val output = buildString {
    JsonWriter(pretty = true) { append(it) }.apply {
        writeStartObject()
        writeFieldName("name")
        writeString("John Smith")
        writeFieldName("age")
        writeInt(32)
        writeEndObject()
    }
}
```

```json
{
  "name": "John Smith",
  "age": 32
}
```

String escaping rejects lone UTF-16 surrogates. Numeric writer methods reject
NaN and infinity. Match each collection opening with one closing call; a writer
accepts one root value.

## Reader tokens

[`JsonReader`](../src/commonMain/kotlin/maryk/json/JsonReader.kt) accepts a string
or a `() -> Char?` supplier. `nextToken()` updates and returns `currentToken`,
which starts as `JsonToken.StartDocument`.

| Token | Meaning |
| --- | --- |
| `StartDocument` | Initial reader state |
| `StartObject` / `EndObject` | Object boundaries |
| `StartArray` / `EndArray` | Array boundaries |
| `FieldName` | Read the actual field name from `value`; `name` identifies the token kind |
| `Value` | Read data from `value` and its category from `type` |
| `EndDocument` | Complete document; a subtype of `Stopped` |
| `Suspended` | Input ended before parsing could finish; a subtype of `Stopped` |
| `JsonException` | Recorded parsing failure; a subtype of `Stopped` |

The shared [`ValueType`](../src/commonMain/kotlin/maryk/json/IsJsonLikeReader.kt)
categories are `String`, `Bool`, `Int`, `Float` and `Null`. Integer tokens contain
Kotlin `Long` values and floating-point tokens contain `Double` values. Invalid
content raises `InvalidJsonContent`; the reader also records the failure token.
Stopping a token loop is not by itself proof of completion: check that its final
token is `EndDocument` when a complete document is required.

For a complete string, handle the token payloads and validate completion:

```kotlin
import maryk.json.JsonReader
import maryk.json.JsonToken

val reader = JsonReader("""{"name":"John Smith","age":32}""")
while (reader.currentToken !is JsonToken.Stopped) {
    when (val token = reader.nextToken()) {
        is JsonToken.FieldName -> println("field: ${token.value}")
        is JsonToken.Value<*> -> println("value: ${token.value}, type: ${token.type}")
        else -> Unit
    }
}
check(reader.currentToken == JsonToken.EndDocument) { "Incomplete JSON document" }
```

For the example above, token values appear in this order:

```text
StartDocument
StartObject
FieldName(name)
Value(John Smith)
FieldName(age)
Value(32)
EndObject
EndDocument
```

The token listing includes the initial `StartDocument`; it is not returned by
the first `nextToken()` call. Commas and colons are consumed internally.

## Input boundaries and limits

A supplier returns `null` at the end of available input. If the reader returns
`Suspended`, retain that same reader, provide more characters and call
`nextToken()` again. Advance the supplier's index only when it returns a
character. `Suspended.lastToken` and `storedValue` describe partial progress;
do not treat them as an additional completed token.

`null` can also complete a root scalar or finish the document. It is therefore
not a safe generic delimiter between arbitrary network chunks: frame or buffer
the complete document before parsing when the input boundary is uncertain.
The reader accepts one root value and rejects trailing non-whitespace content.

Integer values outside the `Long` range and floating-point values that overflow
to infinity are rejected. Strings validate escapes and UTF-16 surrogate pairs.
Object/array nesting is bounded at 128 levels. Streaming avoids an intermediate
tree, but each scalar still needs storage; bound input and scalar sizes at the
application boundary when processing untrusted or very large documents.

## Positions and skipping fields

`lineNumber` and `columnNumber` expose the reader's current position for error
reporting, rather than the full source span of a token.

Call `skipUntilNextField()` while `currentToken` is the unwanted `FieldName`.
It consumes that field's value, including nested arrays and objects, and leaves
the reader at the next peer field, the containing `EndObject`, or a stopped
state. Inspect that token before advancing again. For example:

```kotlin
val reader = JsonReader("""{"ignored":{"nested":[1,2]},"name":"Ada"}""")
reader.nextToken() // StartObject
reader.nextToken() // FieldName(ignored)
reader.skipUntilNextField()
check((reader.currentToken as JsonToken.FieldName).value == "name")
```

Supply the optional callback to collect the skipped value tokens for later
processing with a
[`PresetJsonTokenReader`](../src/commonMain/kotlin/maryk/json/PresetJsonTokenReader.kt).
Its `currentToken` starts at the first supplied token, so consume that token
before calling `nextToken()`; it does not prepend `StartDocument`.
