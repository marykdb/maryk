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
  "key": [["Ref", "username"]],
  "properties": [
    { "index": 0, "name": "username", "definition": ["String", { "required": true, "final": true, "unique": true }] },
    { "index": 1, "name": "email",    "definition": ["String", { "required": true, "unique": true }] }
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
User.writeJson(userValues, JsonWriter(pretty = true) { out.append(it) })
```

## Deserialize Values from JSON
```kotlin
val it = jsonString.iterator()
val reader = JsonReader { if (it.hasNext()) it.nextChar() else Char.MIN_VALUE }
val values = User.readJson(reader)
```

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
// Build a scan that selects only name and filters soft deletes
val req = Requests(
    User.scan(select = User.graph { listOf(name) }, filterSoftDeleted = true)
)

// Context with known models
val defs = DefinitionsContext().apply {
    dataModels["User"] = DataModelReference(User)
}
val ctx = RequestContext(defs)

// Write JSON (pretty)
val out = StringBuilder()
Requests.writeJson(req, JsonWriter(pretty = true) { out.append(it) }, ctx)
```

### Example: Read a ValuesResponse from JSON
```kotlin
val it = jsonString.iterator()
val reader = JsonReader { if (it.hasNext()) it.nextChar() else Char.MIN_VALUE }
val ctx = RequestContext(DefinitionsContext(mutableMapOf("User" to DataModelReference(User))))

// Parse a typed response
val response = ValuesResponse.readJson(reader, ctx)
```

Notes
- Use a `RequestContext` for requests/responses to resolve model names and references.
- Combine multiple operations (Get/Scan/Add/Change/Delete/Collect) inside one `Requests` payload.
