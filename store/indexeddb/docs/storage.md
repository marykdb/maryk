# IndexedDB Storage Layout

One IndexedDB database is opened per Maryk database name.

Object stores are created per model and storage family.

## Global Stores

| Store | Purpose |
| --- | --- |
| `meta` | Storage version, history options, model signatures, encoded model definitions, and migration metadata. |

## Current State Stores

| Store | Key | Value |
| --- | --- | --- |
| `k:modelId` | record key | current metadata and current value snapshot |
| `t:modelId` | key scope + qualifier | encoded current value |
| `i:modelId` | index ref + encoded index value + record key | record key marker |
| `u:modelId` | unique ref + encoded unique value | record key |

Current point reads prefer the key snapshot and table rows. Current table scans cursor over `k:modelId`. Current index scans cursor over `i:modelId`. Unique lookup uses direct `get` on `u:modelId`.

## History Stores

Created when `keepAllVersions` is enabled.

| Store | Key | Value |
| --- | --- | --- |
| `c:modelId` | key scope + version | versioned change payload |
| `ht:modelId` | key scope + inverted version | metadata and value rows snapshot |
| `hi:modelId` | index ref + encoded index value + record key + inverted version | active/tombstone marker |
| `hu:modelId` | unique ref + encoded unique value + inverted version | record key or tombstone |
| `hik:modelId` | key scope + historic index row key | historic index row key |
| `huk:modelId` | key scope + historic unique row key | historic unique row key |

`ht:modelId` lets historic point, table and referenced-property reads resolve the latest snapshot at or before `toVersion`.
Snapshot identities must contain the exact length-prefixed record scope followed
by eight version bytes. Readers skip malformed identities before selecting the
latest snapshot, including across native cursor batches. They leave those rows
untouched. A selected canonical snapshot must have `lastVersion` equal to its
stored row version and `firstVersion <= lastVersion`. Mismatched metadata raises
`StorageException` instead of leaking another revision's values or metadata;
readers do not reconstruct unknown data.

Change-log readers apply inclusive version bounds to the stored row identity
before decrypting or decoding its payload. Excluded payloads cannot make a bounded
query fail. Included `c:modelId` and `uh:modelId` payloads must contain exactly one
revision matching the row's record key and version. A mismatch raises
`StorageException`; readers neither rewrite the row nor attach its changes to a
different record or version. Cross-context change-journal replay applies the same
payload identity check.

`hi:modelId` lets historic index scans use IndexedDB ranges over Maryk index values. The reader collapses the latest visible row per indexed row so historic tombstones are handled correctly.

`hu:modelId` lets historic unique scans resolve the latest visible key for a unique value at or before `toVersion`.

`hik:modelId` and `huk:modelId` are cleanup sidecars. They let hard delete purge only historic secondary rows owned by the deleted object, instead of scanning whole historic secondary stores.

## Update History Store

Created when both `keepAllVersions` and `keepUpdateHistoryIndex` are enabled.

| Store | Key | Value |
| --- | --- | --- |
| `uh:modelId` | inverted version + record key | versioned change payload |

`scanUpdateHistory` uses this store for newest-first history scans.

History row keys must contain exactly eight version bytes followed by the model's
fixed-size record key. Paging and the hard-delete/sensitive-history startup
migrations validate this identity before decoding or deriving other rows.
Malformed short or long identities are skipped without consuming a page entry,
creating deletion metadata, or rewriting the malformed source row.

## Key Scoping

Rows that belong to one object use a length-prefixed object-key scope before the row suffix. This prevents prefix collisions where one raw Maryk key starts with the bytes of another key.

## Sensitive Values

Sensitive current and historic table values are encrypted before being written. Sensitive unique values use deterministic lookup tokens when the provider supports `SensitiveIndexTokenProvider`.

Sensitive indexed properties are rejected. Use an explicit blind-index field instead.

## Incrementing-map lifetime metadata and opening recovery

Allocation maxima live in `meta` below `maryk.incmap/<modelId>/`, followed by the
length-prefixed record key and canonical incrementing-map qualifier. The value is
the key definition's ordinary encoded maximum. Replacements and removed ancestors
retain this lifetime maximum; hard deletion clears it. A separate
`maryk.incmap.verified/<modelId>/` record attests complete recovery of a released
record's allocations and is also cleared on hard deletion.

The released unversioned layout upgrades directly to storage format 2 in the
unreleased 0.14 transition. It physically removed deleted table rows and rebuilt
complete snapshots, so retained collection count `[0]` proves a present empty
collection. Those ordinary bytes are preserved. All collection-bearing schemas
are inventoried even when they have no floating-point properties.

Released incrementing-map maxima already erased by replacements or removals
cannot be inferred from current values. Creation-only records require no history.
Edited records require retained complete snapshots or an externally verified
recovery of every allocation maximum, including removed map instances. Historic
qualifiers seed watermarks before source rows move; interrupted retries never
lower them. Creation and latest snapshot frontiers validate the retained bounds,
but cannot prove that no intermediate snapshots were erased. History must have
been enabled continuously and never pruned. Otherwise recover from an authoritative
application log or backup. Opening fails with the model and record identity if
the evidence is insufficient; writing a recovery attestation alone is not a repair.
Application migrations must remap lifetime metadata when changing record keys,
incrementing-map qualifiers or their key definitions.

## Cross-context commit journal

Commit journal keys contain eight committed-clock bytes and four sequence bytes.
The encoded event version must be at or below that clock; imported historical
events may retain an older event version. Replay rejects malformed identities,
truncated record keys and future events with `StorageException`. The existing
listener failure path asks consumers to resubscribe for a fresh snapshot and
leaves stored entries untouched.

### Ordered checks

Checks compare complete values at their position in the materialized change list.
Earlier changes are applied to a private value view before evaluating the next
Check; a failed Check publishes no rows. Incrementing-map additions are materialized
before this evaluation, and missing parent maps are seeded only for preceding value
writes. Requests without Checks retain the direct materialization path.

## Current values and allocation

Soft deletion preserves the current snapshot payload even when history is
disabled. Older deleted snapshots which omitted that payload fall back to their
retained table cells for reads and filters; an empty cached snapshot does not
erase the retained values.
Self-contained journal entries preserve valid empty deleted records, and
`getUpdates` retains deletion-at-creation even without historic snapshots.
Mutation no-op detection compares ordinary encoded cells before encryption, so
distinct Float32/Float64 NaN payloads retain their unique identities through
changes as well as initial writes. The codecs keep distinct raw patterns they
receive, and encoded cells and unique keys retain those bytes across reopen.

The JavaScript target cannot guarantee arbitrary NaN payload identity through its
`Number` values. In Chrome/V8, assigning a NaN to a numeric object field or array
element can canonicalize its payload before Maryk constructs `Values` or a query
filter. This input limitation does not indicate conversion of existing encoded
cells. Use the Wasm target when storage and query round trips must retain supplied
Float32/Float64 NaN payloads. Floating-point arithmetic may itself change NaN
payloads, and a codec cannot recover bits already canonicalized by its caller.
Finite values and signed zero retain the normal query and uniqueness contract.

Incrementing-map allocation retains a per-record watermark in metadata. Removing
the greatest entry or replacing the map does not reuse allocated keys after
reopen. Watermarks commit with the mutation and are cleared by hard deletion.
Allocation follows preceding value writes in the request and rejects numeric
overflow before changing the record.

## Opening storage format 2

Read the [storage upgrade guide](../../shared/docs/storage-format-upgrades.md)
for rollout, conversion and recovery requirements. The details below describe this
engine's persisted layout.

IndexedDB also converts opaque floating-point value-object bytes in its persisted
Protobuf change logs and update-history payloads; ordinary Float/Double wire values
remain unchanged. Current record snapshots in `k:modelId` are converted alongside
the table rows and historic snapshots. Metadata-only current records and
update-history deletion markers remain unchanged.
The same opening upgrade reframes released nanosecond DateTime collection
payloads in both log stores, including models without floating-point fields.
Encrypted affected logs require their field encryption provider.
Stored source schemas are decoded with their original codec so defaults and
value-object bounds retain their meaning. Before completion, each root schema and
its dependent definitions are rewritten atomically with a per-model source-schema
format marker; reopening after an interrupted rewrite reads the correct codec.
Index descriptors with fixed floating-point map or set ancestors are converted
alongside their current, historic, and cleanup rows, even when their leaf value
uses an unchanged integer encoding. Retained transaction receipts in metadata
are tagged with the released codec so identical retries return their original
response after upgrade and reopen. Ambiguous released request identities stop
with an explicit recovery error rather than running another mutation.
