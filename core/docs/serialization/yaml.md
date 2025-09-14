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
  - Module docs: [YAML module](../../yaml/README.md)

## Model Definition Example
```yaml
name: User
key:
- !Ref username
? 0: username
: !String { required: true, final: true, unique: true }
? 1: email
: !String { required: true, unique: true }
```

## Data Example
```yaml
username: john.smith
email: john.smith@gmail.com
```

## Serialize Values to YAML
```kotlin
val out = StringBuilder()
User.writeYaml(userValues, YamlWriter { out.append(it) })
```

## Deserialize Values from YAML
```kotlin
val it = yamlString.iterator()
val reader = MarykYamlReader { if (it.hasNext()) it.nextChar() else Char.MIN_VALUE }
val values = User.readYaml(reader)
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
// Build a request: get one user by key and only select name
val req = Requests(
    User.get(
        keys = listOf(userKey),
        select = User.graph { listOf(name) }
    )
)

// Build a context with known models
val defs = DefinitionsContext().apply {
    dataModels["User"] = DataModelReference(User)
}
val ctx = RequestContext(defs)

// Write YAML
val out = StringBuilder()
Requests.writeYaml(req, YamlWriter { out.append(it) }, ctx)
```

### Example: Read a ValuesResponse from YAML
```kotlin
val it = yamlString.iterator()
val reader = MarykYamlReader { if (it.hasNext()) it.nextChar() else Char.MIN_VALUE }
val ctx = RequestContext(DefinitionsContext(mutableMapOf("User" to DataModelReference(User))))

// Parse a typed response
val response = ValuesResponse.readYaml(reader, ctx)
```

Notes
- For requests/responses, always pass a `RequestContext` so model and reference names resolve during parsing.
- You can combine multiple operations (Get/Scan/Add/Change/Delete/Collect) in a single `Requests` payload.
