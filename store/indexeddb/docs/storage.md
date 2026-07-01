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
| `k:<modelId>` | record key | current metadata and current value snapshot |
| `t:<modelId>` | key scope + qualifier | encoded current value |
| `i:<modelId>` | index ref + encoded index value + record key | record key marker |
| `u:<modelId>` | unique ref + encoded unique value | record key |

Current point reads prefer the key snapshot and table rows. Current table scans cursor over `k:<modelId>`. Current index scans cursor over `i:<modelId>`. Unique lookup uses direct `get` on `u:<modelId>`.

## History Stores

Created when `keepAllVersions` is enabled.

| Store | Key | Value |
| --- | --- | --- |
| `c:<modelId>` | key scope + version | versioned change payload |
| `ht:<modelId>` | key scope + inverted version | metadata and value rows snapshot |
| `hi:<modelId>` | index ref + encoded index value + record key + inverted version | active/tombstone marker |
| `hu:<modelId>` | unique ref + encoded unique value + inverted version | record key or tombstone |
| `hik:<modelId>` | key scope + historic index row key | historic index row key |
| `huk:<modelId>` | key scope + historic unique row key | historic unique row key |

`ht:<modelId>` lets historic point and table reads resolve the latest snapshot at or before `toVersion`.

`hi:<modelId>` lets historic index scans use IndexedDB ranges over Maryk index values. The reader collapses the latest visible row per indexed row so historic tombstones are handled correctly.

`hu:<modelId>` lets historic unique scans resolve the latest visible key for a unique value at or before `toVersion`.

`hik:<modelId>` and `huk:<modelId>` are cleanup sidecars. They let hard delete purge only historic secondary rows owned by the deleted object, instead of scanning whole historic secondary stores.

## Update History Store

Created when both `keepAllVersions` and `keepUpdateHistoryIndex` are enabled.

| Store | Key | Value |
| --- | --- | --- |
| `uh:<modelId>` | inverted version + record key | versioned change payload |

`scanUpdateHistory` uses this store for newest-first history scans.

## Key Scoping

Rows that belong to one object use a length-prefixed object-key scope before the row suffix. This prevents prefix collisions where one raw Maryk key starts with the bytes of another key.

## Sensitive Values

Sensitive current and historic table values are encrypted before being written. Sensitive unique values use deterministic lookup tokens when the provider supports `SensitiveIndexTokenProvider`.

Sensitive indexed properties are rejected. Use an explicit blind-index field instead.
