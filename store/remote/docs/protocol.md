# Remote HTTP protocol

See the [Remote Store overview](../README.md) for a first connection.

All payloads are Maryk ProtoBuf bytes.
## Request limits and authorization

- `Authorization: Bearer <token>` on every endpoint when server authentication is configured.
- `Content-Type: application/x-maryk-protobuf` on all `POST` endpoints.
- Empty request bodies are rejected.
- Request body max size is 16 MiB.
- Execute batches are limited to 256 requests. Query filters are limited to
  1,024 work units and fetches to 128 aggregations.
- Statically resolvable request type, work, and authorization checks are
  preflighted for the full batch before execution. Mutation batches cannot
  depend on an earlier collected result. Execution is not transactional:
  datastore or response-encoding failures can still occur after an earlier
  request has completed.
- Each response frame is limited to 16 MiB; a batched execute response is limited
  to 64 MiB total.
- Flow updates use rendezvous delivery into the response writer, preventing an
  additional application-level queue of updates from growing ahead of a slow client.

## Endpoints

- `GET /v1/info` → `RemoteStoreInfo` (definitions + model id map + capabilities)
- `GET /v1/snapshot-version` → authoritative 8-byte point-in-time read boundary
- `POST /v1/execute` → `Requests` in. Legacy single-request clients receive one
  raw ProtoBuf response; clients requesting `X-Maryk-Execute-Protocol: 2`
  receive length-prefixed response frames and may send batches.
- `POST /v1/flow` → `Requests` (single fetch) in, stream of length-prefixed `UpdatesResponse`
- `POST /v1/restore-version` → one complete source-version group, committed atomically
- `POST /v1/process-update` → `UpdateResponse` in, `ProcessResponse` out
- `POST /v1/admin/migrations` → versioned migration status/control payloads

Point-in-time export and backup require `GET /v1/snapshot-version`. Upgrade the
Remote client and server together before relying on that guarantee: a newer
client intentionally fails closed when an older server lacks the endpoint rather
than exporting pages from different points in time.

The info response advertises `supportsSnapshotVersion` from the actual backend,
separately from `keepAllVersions`. SQL historical reads require both capabilities.
An older info response without this flag defaults to unsupported; upgrade both
client and server to advertise historical SQL support.

Remote execute protocol changes require a matched client and server deployment.
Upgrade both together before using `X-Maryk-Execute-Protocol: 2` batches or
relying on their framed response contract. Mixed protocol generations are not a
supported batch compatibility target; keep legacy single-request framing only as
a transition aid.

## Streaming format

- Each message is `length (4 bytes, big-endian)` + `ProtoBuf payload`.
- Legacy clients reject zero/negative lengths, truncated frames, trailing bytes,
  and frames larger than 16 MiB.
- Clients requesting `X-Maryk-Flow-Protocol: 2` accept zero-length heartbeat frames.
  The payload frame format remains unchanged.

## Remote regular-expression filters

`RegEx` and `MatchesRegEx` accept a bounded subset on the server: at most 256
pattern characters, literal characters, outer `^`/`$` anchors, `.`, flat character
classes, escaped literal punctuation or `\d`, `\D`, `\s`, `\S`, `\w`, `\W`,
`\n`, `\r`, `\t`, `\f`, and at most one `*`, `+`, or `?` repetition of a
consuming character atom. For example, `^garcia.*$` remains supported.

Groups, alternation, counted repeats, backreferences, lookarounds, flags, nested or
intersected classes, and multiple repetitions return HTTP 400 before datastore
execution. The same check applies to nested filters, collected reads, flows, and
mutation batches. This bounds backtracking in full-string matching; local trusted
queries retain the complete regex API. Exact and prefix named search remain
available through `Matches` and `MatchesPrefix`.

## Transport format compatibility

Models with floating-point fields inside packed value objects or NANOS DateTime
properties require matching transport formats on the client and server. Store
info advertises the format, and requests send `X-Maryk-Value-Storage-Format`;
incompatible peers are rejected before decoding or applying requests. This also
protects NANOS DateTime collections, whose released packed pairs can resemble
one current framed value. Upgrade both peers together. Ordinary Float/Double
Protobuf scalar fields retain their wire format.
