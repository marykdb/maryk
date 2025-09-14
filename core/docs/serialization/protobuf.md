# ProtoBuf Transport

Maryk uses the [ProtoBuf v3](https://developers.google.com/protocol-buffers/) encoding for compact, fast, binary transport of data values. You don’t need `.proto` files: encoding is implemented directly against your Maryk models with stable property indexes.

## What You Can Serialize
- Data: values for any RootDataModel.
- Requests & Responses: compactly encode `Requests` envelopes and typed responses for store interactions.
- Models: Maryk does not generate `.proto` schema files; use YAML/JSON to exchange model definitions instead. See [Serialization Overview](README.md).

## When to Use
- Service‑to‑service or store transport where both ends are Maryk‑aware.
- Large payloads and latency‑sensitive paths that benefit from compact binary.

## How It Works

### Key–Value Pairs
ProtoBuf messages consist of key–value pairs. Each key combines a field tag (here: the property’s stable index) with a wire type that defines how the value is encoded. The [Properties documentation](../properties/README.md) lists the encoding used for each property type.

### Wire Types
Maryk uses the standard ProtoBuf wire types:
- VarInt — variable‑length integers for numeric values.
- Length‑Delimited — values prefixed by byte length; also used for embedded messages.
- 32‑bit — fixed‑width 4‑byte values.
- 64‑bit — fixed‑width 8‑byte values.

For background details and examples, see the official [ProtoBuf encoding documentation](https://developers.google.com/protocol-buffers/docs/encoding).

## Optimized Writing
- Precompute message size for buffer allocation: `calculateProtoBufLength(values, cache)`.
- Use `WriteCache` to reuse computed sizes across nested values.
- Write directly to a byte sink.

### Example: Serialize to Bytes
```kotlin
val cache = WriteCache()
val byteLength = User.Serializer.calculateProtoBufLength(userValues, cache)
// Reserve a buffer of byteLength if needed
User.Serializer.writeProtoBuf(userValues, cache, byteSink::write)
```

### Example: Deserialize from Bytes
```kotlin
val values = User.Serializer.readProtoBuf(byteLength, byteSource::read)
```

## Compatibility and Versioning
- Stable property indexes let decoders skip unknown fields safely, enabling forward/backward compatibility. See [Versioning](../versioning.md).
- Keep indexes stable across versions; add new fields with new indexes instead of renaming/reusing.

## Reading Without a Compiled Model
You can read index→value pairs when a compiled model isn’t present, useful for tooling and diagnostics.

## Tips
- Combine with property graphs to serialize only required fields, reducing bytes on the wire. See [Selecting with Graphs](../reference-graphs.md).
- Length‑delimited embedded values make nested selections efficient.

## Related
- [Serialization Overview](../serialization/README.md)
- [Data Models](../datamodel.md)
- [Properties](../properties/README.md)

## Requests & Responses

Requests and responses are first‑class ProtoBuf citizens. Use the `Requests` envelope to batch operations, and provide a `RequestContext` to compute sizes and serialize correctly (it also injects additional metadata where ProtoBuf requires it).

### Example: Serialize a batched Requests payload
```kotlin
val req = Requests(
    User.get(keys = listOf(userKey), select = User.graph { listOf(name) }),
    User.scan(select = User.graph { listOf(name) })
)

// Context with known models
val defs = DefinitionsContext().apply { dataModels["User"] = DataModelReference(User) }
val ctx = RequestContext(defs)

val cache = WriteCache()
val byteLength = Requests.Serializer.calculateProtoBufLength(req, cache, ctx)
Requests.Serializer.writeProtoBuf(req, cache, byteSink::write, ctx)
```

### Example: Deserialize a ValuesResponse
```kotlin
val ctx = RequestContext(DefinitionsContext(mutableMapOf("User" to DataModelReference(User))))
val resp = ValuesResponse.readProtoBuf(byteLength, byteSource::read, ctx)
```

Notes
- For batched requests, ProtoBuf encodes “injectables” at the envelope level; `RequestContext` ensures they are computed and written.
