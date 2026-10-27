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
- Stable property indexes let decoders skip standard wire fields safely, enabling forward/backward compatibility. See [Versioning](../versioning.md) and the NANOS DateTime compatibility notes below.
- Keep indexes stable across versions; add new fields with new indexes instead of renaming/reusing.

### NANOS DateTime compatibility

`DateTimeDefinition(precision = NANOS)` writes a length-delimited message with
`int64 seconds = 1` and `uint32 nanos = 2`. Proto3 generation emits this nested
message too. Unknown-field readers can skip it without consuming following
fields. Seconds use the existing UTC convention; nanos must be 0..999,999,999.
Ordinary fixed-width DateTime storage, JSON/YAML and SECONDS/MILLIS transport are
unchanged.

For a known NANOS scalar, the reader distinguishes the legacy VAR_INT tag from
the new LENGTH_DELIMITED tag. It reads the old seconds/nanos pair, then always
writes the new message. No per-value version field or database payload rewrite
is needed for this scalar compatibility path. Old writers/readers must be
upgraded before exchanging the new representation.

Legacy packed NANOS lists/sets are ambiguous with the new message framing and
are not covered by this automatic compatibility path. Convert such inputs with
their known old schema/codec before upgrading; do not guess a format from their
contents. Legacy unknown NANOS fields also cannot be skipped reliably because
the old writer put two varints under one scalar tag.

Protobuf is also used for persisted model definitions, IndexedDB journals,
FoundationDB cluster update logs and restore staging. Stop old writers during
the upgrade, restart subscribers from a fresh snapshot, and create backups with
the upgraded library. There is no added legacy log, encryption or backup wrapper.

### Complete collection values and empty presence

Whole-property reference pairs (`Change`, `Check`, `Equals`, and `ValueIn`
alternatives) resolve scalar and collection definitions. Collection values use a
length-delimited envelope containing the property's ordinary entries at tag 1;
entries accumulate within that envelope. An empty envelope represents an empty
replacement, while an absent value in a nullable Change pair still means removal.
Scalar reference-pair encodings retain their existing tags and bytes.

Ordinary model values need separate presence for empty nonpacked collections and
maps: no ordinary entries would otherwise be indistinguishable from an absent
property. The model serializer emits a zero-length, length-delimited field at
`32768 + propertyIndex` for these empty values. Property transport indexes are
limited to 1..32767, so the marker cannot collide with a supported property tag.
Packed numeric collections retain their existing empty encoding. Nonempty values
and storage formats are unchanged; no datastore migration is required.

Maryk-aware readers restore these markers to empty collections and reject malformed
marker lengths or conflicting earlier values. Older Maryk readers and generated
Proto3 clients skip the presence marker as an unknown field and still observe an
absent property. Upgrade both transport endpoints when empty versus absent matters.
Older scalar-only reference-pair codecs cannot read the new whole-collection
envelopes; upgrade both endpoints before using these formerly unsupported requests.

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
