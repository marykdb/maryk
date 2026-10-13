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

## Cross-context commit journal

Commit journal keys contain eight committed-clock bytes and four sequence bytes.
The encoded event version must be at or below that clock; imported historical
events may retain an older event version. Replay rejects malformed identities,
truncated record keys and future events with `StorageException`. The existing
listener failure path asks consumers to resubscribe for a fresh snapshot and
leaves stored entries untouched.
