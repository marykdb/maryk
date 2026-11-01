# JSON Serialization

JSON is widely supported and easy to integrate with external services, tools, and browsers. Maryk supports JSON for both models and data.

## What You Can Serialize

- Models: define and exchange DataModels in JSON.
- Data: serialize/deserialize values for any model.
- Requests & Responses: encode `Requests` envelopes and typed responses (e.g., `ValuesResponse`) to interact with stores.

## Why JSON

- Broad interoperability across languages and platforms.
- Human‑readable with optional pretty printing.

## Parsers and Writers

- Maryk ships a streaming JSON module (Kotlin Multiplatform) to avoid large intermediate trees and ensure consistent behavior.
  - Writer: `maryk.json.JsonWriter`
  - Reader: `maryk.json.JsonReader`
  - Module docs: [JSON module](../../../json/README.md)

## Model Definition Example
```json
{
  "name": "User",
  "properties": [
    { "index": 1, "name": "username", "definition": ["String", { "required": true, "final": true, "unique": true }] },
    { "index": 2, "name": "email",    "definition": ["String", { "required": true, "unique": true }] }
  ]
}
```

## Data Example
```json
{ "username": "john.smith", "email": "john.smith@gmail.com" }
```

## Serialize Values to JSON (Pretty)
```kotlin
val out = StringBuilder()
User.Serializer.writeJson(userValues, JsonWriter(pretty = true) { out.append(it) })
```

## Deserialize Values from JSON
```kotlin
val it = jsonString.iterator()
val reader = JsonReader { if (it.hasNext()) it.nextChar() else null }
val values = User.Serializer.readJson(reader)
```

Model and collection decoders require the closing `}` or `]` before returning a
value. Truncated input is rejected even when earlier fields or items were read.
Custom decoders for reference/value pairs, collection mutations, comparison
filters, and property-reference graphs also require all their inner and outer
closing tokens. A truncated mutation or selection does
not become a partially decoded request. A named `CollectRequest` wrapper contains
exactly one request; extra fields are rejected.
A model with no declared fields still requires an object (`{}`); well-formed
unknown fields are skipped using the same rules as other models.

## Interoperability

- Ideal for public APIs and cross‑language tooling.
- Combine with property graphs to limit fields and keep payloads small. See [Selecting with Graphs](../reference-graphs.md).

## Related

- [Data Models](../datamodel.md)
- [Properties](../properties/README.md)

## Requests & Responses

JSON can also carry store requests and responses. Wrap operations in `Requests` and pass a `RequestContext` for parsing/writing.

### Example: Serialize a Scan request
```kotlin
// Build a scan that selects only username and filters soft deletes
val req = Requests(
    User.scan(select = User.graph { listOf(username) }, filterSoftDeleted = true)
)

// Context with known models
val defs = DefinitionsContext().apply {
    dataModels["User"] = DataModelReference(User)
}
val ctx = RequestContext(defs)

// Write JSON (pretty)
val out = StringBuilder()
Requests.Serializer.writeObjectAsJson(req, JsonWriter(pretty = true) { out.append(it) }, ctx)
```

### Example: Read a ValuesResponse from JSON
```kotlin
val it = jsonString.iterator()
val reader = JsonReader { if (it.hasNext()) it.nextChar() else null }
val ctx = RequestContext(DefinitionsContext(mutableMapOf("User" to DataModelReference(User))))

// Parse a typed response
val response = ValuesResponse.Serializer.readJson(reader, ctx).toDataObject()
```

Notes
- Batched requests run in order and stop at the first failure; they are not atomic. Use an explicit [write boundary](../query.md#choosing-a-write-boundary) when all writes must commit together.
- Use a `RequestContext` for requests/responses to resolve model names and references.
- Combine multiple operations (Get/Scan/Add/Change/Delete/Collect) inside one `Requests` payload.

## Floating-point text identity

JSON and YAML preserve raw NaN bits in ordinary Float32/Float64 values, including
collection values, query operands and schema defaults. The standard positive
canonical NaN keeps its existing `NaN` spelling. Other NaNs use `NaN:0x7fc00001`
for Float32 or `NaN:0x7ff8000000000001` for Float64: exactly 8 or 16 hexadecimal
digits encoding a NaN at the declared width. Finite nonzero values and infinity
keep their existing spellings. Negative zero always uses `-0.0`, including on
JavaScript where the native numeric string conversion drops its sign. This also
preserves distinct signed-zero map keys and collection reference names. JSON
quotes floating values as before.

Older text readers do not understand the explicit raw-bit form; update both
endpoints before exchanging it. Existing text containing only `NaN` cannot
recover a discarded payload. ProtoBuf and stored numeric bytes are unchanged,
so this adds no storage migration. Numeric map keys and set-item reference names
keep canonical NaN identity. JavaScript may canonicalize a NaN before a serializer
receives it; explicit text cannot recover bits already lost at that boundary.
