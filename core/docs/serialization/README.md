# Serialization

Maryk transports three things: your data models, the data itself, and the requests/responses exchanged with data stores. Models describe structure and constraints; values are concrete instances; requests/responses are the operations and results you send/receive when talking to a store. Maryk supports three serialization formats:

- YAML — human‑readable; great for model definitions, examples, and hand‑edited data.
- JSON — widely interoperable; useful for external integrations and browser/HTTP.
- ProtoBuf — compact binary; best for high‑throughput internal transport.

## Models vs. Data vs. Requests/Responses

- Models (YAML/JSON): Define and exchange DataModels. These formats are easy to inspect, diff, and use for tooling or code generation. See [Data Models](../datamodel.md).
- Data (YAML/JSON/ProtoBuf): Serialize model values in all three formats. ProtoBuf is typically preferred in production for its size and speed.
- Requests/Responses (YAML/JSON/ProtoBuf): Serialize batch [Requests](https://github.com/marykdb/maryk/blob/main/core/src/commonMain/kotlin/maryk/core/query/requests/Requests.kt) and typed responses (e.g., [ValuesResponse](https://github.com/marykdb/maryk/blob/main/core/src/commonMain/kotlin/maryk/core/query/responses/ValuesResponse.kt)). Requests use a stable multi‑type envelope so multiple operations (Get/Scan/Add/Change/Delete/…) can be combined in one payload.

## Choosing a Format

- Internal hot paths or store transport: ProtoBuf.
- Public/partner APIs and tooling: JSON.
- Documentation, config, demos, migrations: YAML.

## How Maryk Optimizes Transport

- **Stable property indexes** — Each property has a stable `UInt` index. Encoders write compact tags without reflection, and decoders can skip unknown fields safely. See [Versioning](../versioning.md).
- **Streaming encoders/decoders** — YAML and JSON use streaming writers/readers, avoiding large intermediate trees. See the module docs for [YAML](../../yaml/README.md) and [JSON](../../json/README.md).
- **ProtoBuf write cache** — Compute size once with `calculateProtoBufLength` and stream to a byte sink using `writeProtoBuf` and `WriteCache`.
- **Select just what you need** — Query using property reference graphs to return only required fields before serialization. See [Selecting with Graphs](../reference-graphs.md).

## Examples at a Glance

- Models (YAML/JSON): Define a model and read it at runtime.
- Data (all formats): Write/read `Values` for a model.
- Requests (all formats): Wrap operations in `Requests` and serialize with a `RequestContext` that knows your models.

See the format pages for end‑to‑end code.

## Format Guides

- [YAML](yaml.md)
- [JSON](json.md)
- [ProtoBuf Transport](protobuf.md)

## Related Topics

- [Properties](../properties/README.md)
- [Property References](../properties/references.md)
- [Versioning](../versioning.md)
