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

Legacy packed NANOS lists/sets are ambiguous with the new message framing.
The opening storage upgrade and FoundationDB log reader use their enclosing
format metadata to select `readLegacyTransportStorage` explicitly, then write
current messages. This also covers stored schema defaults and map keys/values.
For independent legacy payloads, use that scope on a `RequestContext` or
`DefinitionsConversionContext` only when their source format is known; never
guess from payload contents. Legacy unknown NANOS fields cannot be skipped reliably because
the old writer put two varints under one scalar tag.

Protobuf is also used for persisted model definitions, IndexedDB journals,
FoundationDB cluster update logs and restore staging. Stop old writers during
the upgrade, restart subscribers from a fresh snapshot, and create backups with
the upgraded library. Remote peers negotiate format 2 for NANOS DateTime models
as well as packed floating value objects. There is no added encryption or backup wrapper.

### Repeated map entries

A later complete entry for the same map key replaces the earlier entry, including
collection and embedded values. Key/value field order within an entry does not
change this behavior. Repeated collection or map value fields within one entry
still accumulate to form that entry's value. This does not change writer bytes or
stored values; it prevents a decoder from merging two separate map entries.

Repeated typed fields continue a payload only while their case remains the same.
A different case discards the earlier payload. Contextual readers forward these
continuations, and context-transforming writers use the resolved definition's
field tag, wire type and length. Readers check wire types at model, typed-value
and collection-envelope boundaries.

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
Packed numeric collections retain their existing empty encoding. Contextual schema
defaults use the same property marker after resolving their collection definition.
An empty map-entry collection value uses tag `32768 + 2` inside that entry. An empty
collection or map multi-type case uses tag `65536 + caseIndex` inside its envelope;
this range cannot collide with supported enum case IDs through 65535. These nested
markers also have zero-length, length-delimited payloads. Nonempty values and
storage value formats are unchanged; no datastore value migration is required.

Maryk-aware readers restore these markers to empty collections and reject malformed
marker lengths or conflicting earlier values. Older Maryk readers and generated
Proto3 clients skip the presence marker as an unknown field and still observe an
absent property. Upgrade both transport endpoints when empty versus absent matters. Older readers
cannot restore contextual empty defaults and reject the previously unsupported
empty map-entry or multi-type collection values after skipping or failing to
resolve their nested markers.
Older scalar-only reference-pair codecs cannot read the new whole-collection
envelopes; upgrade both endpoints before using these formerly unsupported requests.

Incrementing-map append commands also use a whole-value envelope for each
collection or map element in `addValues` (field 2). This preserves empty elements,
element counts and append order. Scalar and embedded command values keep their
existing bytes. Models with collection-valued incrementing maps, including nested
models, participate in storage-format transport negotiation.

Released field-2 bytes flattened these collection/map elements, so their original
boundaries cannot be recovered reliably. Under an explicitly legacy transport
scope, readers reject an actual affected command instead of guessing. Empty or
absent append commands remain no-ops. Existing persisted logs containing these
commands require reconstruction from a known source or a fresh snapshot before
conversion; rewriting the flattened payload cannot restore the lost boundaries.
Upgrade both transport endpoints for collection-valued append commands. This is
part of the same unreleased storage-format-2 transition and adds no per-record
format metadata.

Incrementing-map addition responses include optional `addedValues` (field 3) when
available, so repeated appends to the same property retain their exact key/value
pairs without reconstructing values from the first matching request. Collection
and map values use a whole-value envelope per appended element. Scalar and embedded
values keep the existing element encoding. Readers still support older responses
that omit field 3 through the request-context fallback; upgrade both endpoints
before relying on self-contained collection-valued additions. Mismatched key/value
counts are rejected. Durable transaction receipts omit these echoed values and
recover them from the exact original operation when replaying a receipt.

Receipt fingerprint comparison can reproduce the released request bytes inside
`RequestContext.writeReleasedTransportForFingerprint`. The scope covers both size
calculation and writing with a fresh `WriteCache`; ordinary writers always retain
the current format. It restores released NANOS framing and packed floating
value-object bytes, including map/set reference keys. These bytes are only for
matching retained receipts and must not be sent as new requests or stored as new
values. Non-injective released encodings, including collection-valued append
commands and empty nonpacked collection multi-type cases, throw
`ReleasedTransportFingerprintException` instead of claiming a request identity.

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
