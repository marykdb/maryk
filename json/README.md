# Maryk JSON

Streaming JSON readers and writers for Kotlin Multiplatform. Use this module
when you need character-based parsing or output without building an intermediate
JSON tree. For Maryk models and values, start with the
[serialization guide](../core/docs/serialization/json.md).

## Writing JSON

```kotlin
import maryk.json.JsonWriter

val output = buildString {
    JsonWriter(pretty = true) { append(it) }.apply {
        writeStartObject()
        writeFieldName("name")
        writeString("Ada")
        writeEndObject()
    }
}
```

The writer also accepts any `(String) -> Unit` output sink. It escapes string
content and rejects non-finite numeric values.

## Reading JSON

```kotlin
import maryk.json.JsonReader
import maryk.json.JsonToken

val input = """{"name":"Ada"}"""
var index = 0
val reader = JsonReader { input.getOrNull(index++) }
while (reader.currentToken !is JsonToken.Stopped) {
    println(reader.nextToken())
}
```

A reader consumes one character at a time; return `null` when no input is
available. `EndDocument` means completion, while `Suspended` means more input
is needed. Invalid content raises a parsing error.

See the [streaming API guide](docs/streaming.md) for tokens, numeric types,
writer methods, skipping fields and position tracking.
