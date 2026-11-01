# ProtoBuf Transport

Maryk uses the [ProtoBuf v3](https://developers.google.com/protocol-buffers/) encoding for compact, fast, binary transport of data values. You don’t need `.proto` files: encoding is implemented directly against your Maryk models with stable property indexes.

## What You Can Serialize
- Data: values for any RootDataModel.
- Requests & Responses: compactly encode `Requests` envelopes and typed responses for store interactions.
- Models: use YAML/JSON to exchange model definitions. The [generator](../../../generator/README.md) can export Proto3 schemas for interoperability; Maryk-to-Maryk encoding does not require generated `.proto` files. See [Serialization Overview](README.md).

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

### Embedded messages

An embedded object or embedded values property is a length-delimited field.
Its payload contains the nested model's fields, each with its own property index
and wire type. The outer length is the byte size of that payload.

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

Keep property indexes stable and upgrade both transport endpoints when introducing
new wire representations. The current source branch adds framing and presence
rules that older readers do not always understand.

### NANOS DateTime compatibility

NANOS DateTime uses a framed seconds/nanos message. Known legacy scalar fields can
be read, but packed legacy collections need authoritative source-format metadata.
See [NANOS compatibility](protobuf-compatibility.md#nanos-datetime-compatibility).

### Repeated map entries

A complete entry replaces an earlier entry for the same key. Repeated fields
inside that entry follow its collection, message and typed-case continuation rules.
See [repeated entries](protobuf-compatibility.md#repeated-map-entries).

### Complete collection values and empty presence

Empty and absent collections have distinct encodings. Older/generated readers may
skip Maryk presence markers. See [collection compatibility](protobuf-compatibility.md#complete-collection-values-and-empty-presence)
before exchanging empty values or collection-valued incrementing-map commands.

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
    User.get(userKey, select = User.graph { listOf(username) }),
    User.scan(select = User.graph { listOf(username) })
)

// Context with known models
val defs = DefinitionsContext().apply { dataModels["User"] = DataModelReference(User) }
val ctx = RequestContext(defs)

val cache = WriteCache()
val byteLength = Requests.Serializer.calculateObjectProtoBufLength(req, cache, ctx)
Requests.Serializer.writeObjectProtoBuf(req, cache, byteSink::write, ctx)
```

### Example: Deserialize a ValuesResponse
```kotlin
val ctx = RequestContext(DefinitionsContext(mutableMapOf("User" to DataModelReference(User))))
val resp = ValuesResponse.Serializer.readProtoBuf(byteLength, byteSource::read, ctx).toDataObject()
```

Notes
- Batched requests run in order and stop at the first failure; they are not atomic. Use an explicit [write boundary](../query.md#choosing-a-write-boundary) when all writes must commit together.
- For batched requests, ProtoBuf encodes “injectables” at the envelope level; `RequestContext` ensures they are computed and written.
