# YAML Serialization

YAML is a human‑readable format suited for model definitions and example data. Maryk supports YAML for both models and data.

## What You Can Serialize

- Models: author and exchange DataModel definitions (schemas). See [Data Models](../datamodel.md).
- Data: serialize/deserialize values for any model.
- Requests & Responses: encode `Requests` envelopes and typed responses (e.g., `ValuesResponse`) to interact with stores.

## Why YAML

- Easy to read and review in pull requests.
- Supports anchors, aliases, comments, and complex keys used by Maryk’s definitions.

## Parsers and Writers

- Maryk provides its own streaming YAML module to ensure consistent behavior across platforms and to support required features.
  - Writer: `maryk.yaml.YamlWriter`
  - Reader: `maryk.core.yaml.MarykYamlReader`
  - Module docs: [YAML module](../../../yaml/README.md)

## Model Definition Example
```yaml
name: User
? 1: username
: !String { required: true, final: true, unique: true }
? 2: email
: !String { required: true, unique: true }
```

## Data Example
```yaml
username: john.smith
email: john.smith@gmail.com
```

Maryk serializers use the JSON-like reader/writer interface for both JSON and YAML.
Pass `YamlWriter` or `MarykYamlReader` to `Serializer.writeJson` / `readJson`;
there are no separate `writeYaml` / `readYaml` model methods.

## Serialize Values to YAML
```kotlin
val out = StringBuilder()
User.Serializer.writeJson(userValues, YamlWriter { out.append(it) })
```

## Deserialize Values from YAML
```kotlin
val it = yamlString.iterator()
val reader = MarykYamlReader { if (it.hasNext()) it.nextChar() else null }
val values = User.Serializer.readJson(reader)
```

## Interoperability

- YAML works well for human‑authored content and demos. For production transport, prefer ProtoBuf or JSON.

## Related

- [Properties](../properties/README.md)
- [Selecting with Graphs](../reference-graphs.md)

## Requests & Responses

YAML can also carry store requests and responses. Use the `Requests` envelope to send multiple operations in one payload, and provide a `RequestContext` so names and references resolve correctly.

### Example: Serialize a Get request
```kotlin
// Build a request: get one user by key and only select username
val req = Requests(
    User.get(
        userKey,
        select = User.graph { listOf(username) }
    )
)

// Build a context with known models
val defs = DefinitionsContext().apply {
    dataModels["User"] = DataModelReference(User)
}
val ctx = RequestContext(defs)

// Write YAML
val out = StringBuilder()
Requests.Serializer.writeObjectAsJson(req, YamlWriter { out.append(it) }, ctx)
```

### Example: Read a ValuesResponse from YAML
```kotlin
val it = yamlString.iterator()
val reader = MarykYamlReader { if (it.hasNext()) it.nextChar() else null }
val ctx = RequestContext(DefinitionsContext(mutableMapOf("User" to DataModelReference(User))))

// Parse a typed response
val response = ValuesResponse.Serializer.readJson(reader, ctx).toDataObject()
```

Notes
- Batched requests run in order and stop at the first failure; they are not atomic. Use an explicit [write boundary](../query.md#choosing-a-write-boundary) when all writes must commit together.
- For requests/responses, always pass a `RequestContext` so model and reference names resolve during parsing.
- You can combine multiple operations (Get/Scan/Add/Change/Delete/Collect) in a single `Requests` payload.
