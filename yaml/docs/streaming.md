# Streaming YAML API

Start with the [module overview](../README.md) for a first reader and writer.
For Maryk models and values, use the
[serialization guide](../../core/docs/serialization/yaml.md).

## Writer methods and collection styles

[`YamlWriter`](../src/commonMain/kotlin/maryk/yaml/YamlWriter.kt) accepts a
`(String) -> Unit` output callback. It shares the
[JSON-like writer interface](../../json/docs/streaming.md#writer-methods).
`writeString` quotes or escapes strings when YAML syntax would otherwise change
their meaning. `writeValue` writes scalar text without that protection; use it
only when the caller deliberately controls the YAML representation.

- `writeStartObject(isCompact = false)` opens a block mapping; `true` uses `{...}`.
- `writeStartArray(isCompact = false)` opens a block sequence; `true` uses `[...]`.
- `writeEndObject()` and `writeEndArray()` close those collections. Empty sequences
  remain `[]`, preserving their distinction from null.
- `writeFieldName`, numeric methods, `writeBoolean` and `writeNull` write values
  through the shared interface.
- `writeTag(tag)` writes a tag, including the supplied `!` prefix.
- `writeStartComplexField()` and `writeEndComplexField()` delimit a complex map
  key, which can itself contain an array or object.

For example, `writeString("true")` preserves a string, whereas
`writeBoolean(true)` emits a boolean. Unlike JSON, YAML numeric methods support
non-finite values: `writeDouble` and `writeFloat` emit `.nan`, `.inf` or `-.inf`.
Text methods reject lone UTF-16 surrogates. Invalid collection/field ordering
raises `IllegalJsonOperation`; create a new writer for each output document.

For example, write a nested sequence:

```kotlin
import maryk.yaml.YamlWriter

val output = buildString {
    YamlWriter { append(it) }.apply {
        writeStartObject()
        writeFieldName("name")
        writeString("John Smith")
        writeFieldName("age")
        writeInt(32)
        writeFieldName("pets")
        writeStartArray()
        writeStartObject()
        writeFieldName("name")
        writeString("Muffin")
        writeFieldName("type")
        writeString("CAT")
        writeEndObject()
        writeEndArray()
        writeEndObject()
    }
}
```

```yaml
name: John Smith
age: 32
pets:
- name: Muffin
  type: CAT
```

Open the root collection once and close it after every field has been written.
Closing it early or twice is rejected.

## Reader options

[`YamlReader`](../src/commonMain/kotlin/maryk/yaml/YamlReader.kt) accepts either a
string or a `() -> Char?` supplier. Return `null` at the actual end of input;
YAML can finish implicit block collections at that point. Buffer a complete
document before parsing when a network or file adapter can temporarily have no
characters available. Optional settings include:

| Option | Purpose |
| --- | --- |
| `defaultTag` | Application namespace used for local `!` tags |
| `tagMap` | Map tag namespaces and names to token types |
| `allowUnknownTags` | Return `UnknownYamlTag` for unknown local tags; defaults to `false` |
| `aliasLimits` | Bound alias replay with `YamlAliasLimits` |

Default alias limits are 50 aliases, 100,000 expanded tokens, nesting depth 10
and alias-name length 256 per document. See
[`YamlAliasLimits`](../src/commonMain/kotlin/maryk/yaml/YamlAliasLimits.kt) for
configuration. Raising these limits increases the work permitted for alias
expansion.

For example, explicitly lower the replay limits for a complete input:

```kotlin
import maryk.json.JsonToken
import maryk.yaml.YamlAliasLimits
import maryk.yaml.YamlReader

val reader = YamlReader(
    yaml = "name: Ada\nactive: true\n",
    aliasLimits = YamlAliasLimits(maxAliasCount = 10, maxExpandedTokens = 1_000),
)
while (reader.currentToken !is JsonToken.Stopped) {
    when (val token = reader.nextToken()) {
        is JsonToken.FieldName -> println("field: ${token.value}")
        is JsonToken.Value<*> -> println("value: ${token.value}")
        else -> Unit
    }
}
check(reader.currentToken == JsonToken.EndDocument) { "Incomplete YAML document" }
```

## Tags and scalar types

Plain scalars are resolved by the YAML reader, rather than treated as JSON
strings. Quote ambiguous text or use `!!str` when a string is required.
For example, `true` becomes a boolean while `'true'` remains a string.
Standard tags such as `!!int`, `!!binary` and `!!timestamp` select typed values.

Application tags use the supplied namespace and token types. For example,
map a local `!Person` mapping to an application-defined type:

```kotlin
import maryk.json.MapType
import maryk.yaml.YamlReader

object PersonTag : MapType

val taggedReader = YamlReader(
    yaml = "!Person {name: Ada}",
    defaultTag = "tag:example.org,2026:",
    tagMap = mapOf("tag:example.org,2026:" to mapOf("Person" to PersonTag)),
)
```

The object-start token carries `PersonTag`; it does not instantiate a Kotlin
`Person` object. Unknown local tags require `allowUnknownTags = true`; inspect
the resulting `UnknownYamlTag.name` before interpreting their content. Configure
`defaultTag` for these local tags; allowing unknown tags does not recognize an
otherwise unconfigured tag namespace.

## Tokens and document boundaries

`nextToken()` updates and returns `currentToken`, initially `StartDocument`.
YAML uses the shared [JSON tokens](../../json/docs/streaming.md#reader-tokens),
plus `StartComplexFieldName` and `EndComplexFieldName` for complex keys.

Object and array starts expose their mapping/sequence type. Value tokens expose
both `value` and `type`; field-name tokens store the actual name in `value`.
YAML adds tag-specific value and collection types, including timestamps, binary
values, sets, ordered maps and application-defined tags.

Check for `EndDocument` when a complete document is required. `Suspended` also
stops a token loop but means that more input is needed. Explicit flow maps and
sequences require closing `}` or `]`, including after tags or anchors. Implicit
block collections retain their normal end-of-input behavior. Invalid input
raises `InvalidYamlContent`, which extends `InvalidJsonContent` and includes
line/column information; catch the exception rather than waiting for an error
token.

The reader also recognizes `---` document separators and `...` stream endings.
Another `StartDocument` can appear between documents; a simple loop over tokens
does not enforce a single-document application format. `EndDocument` at `...`
also does not prove that the entire supplied source was consumed. Enforce
single-document input at the application's framing boundary when required,
and reject additional `StartDocument` tokens before accepting its data.

## Positions, skipping and aliases

`lineNumber` and `columnNumber` expose the current reader position.
`skipUntilNextField()` skips a value of any nesting depth; its optional callback
can retain skipped tokens. Call it at the unwanted field-name token and inspect
the next peer field or closing token it leaves in `currentToken` before
advancing. `pushToken()` queues a token to be returned by a later `nextToken()`
call; it does not rewind characters or reset the parser's structural state.

Anchors (`&name`) and aliases (`*name`) reuse document content within the alias
limits. Merge fields (`<<`) can combine mappings through aliases.

```yaml
defaults: &defaults
  enabled: true
profile:
  <<: *defaults
  name: Ada
```

The reader replays the anchored fields into `profile`; consumers see ordinary
field/value tokens rather than having to resolve the alias themselves. Anchors
are local to one document, and aliases must refer to an already recorded anchor.

In addition to the configurable alias replay limits, the reader bounds collection
nesting to 128 levels and retained anchor captures to 100,000 token copies per
document. Alias limits are not a total input or heap budget: scalar strings and
binary values still occupy memory. Apply an input-size limit appropriate to the
application.
