# Maryk YAML

Streaming YAML readers and writers for Kotlin Multiplatform, using the shared
JSON-like token interface. The module supports tags, complex keys, anchors,
aliases and merge fields. For Maryk models and values, start with the
[serialization guide](../core/docs/serialization/yaml.md).

## Writing YAML

```kotlin
import maryk.yaml.YamlWriter

val output = buildString {
    YamlWriter { append(it) }.apply {
        writeStartObject()
        writeFieldName("name")
        writeString("Ada")
        writeEndObject()
    }
}
```

The writer accepts any `(String) -> Unit` sink and quotes strings when needed.
Open and close the root collection once, after writing all its contents.

## Reading YAML

```kotlin
import maryk.json.JsonToken
import maryk.yaml.YamlReader

val input = "name: Ada\n"
var index = 0
val reader = YamlReader { input.getOrNull(index++) }
while (reader.currentToken !is JsonToken.Stopped) {
    println(reader.nextToken())
}
```

A reader consumes one character at a time. Return `null` when no input is
available and distinguish `EndDocument` from `Suspended`. Explicit flow maps and
sequences require their closing delimiters. Alias expansion is bounded.

See the [streaming API guide](docs/streaming.md) for tokens, tags, reader options,
alias limits, collection styles and skipping fields.
