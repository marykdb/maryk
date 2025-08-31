# Maryk + FoundationDB: Storage Layout

This document explains how Maryk stores data in FoundationDB (FDB): what subspaces are used, how keys and values are encoded, and how versioning, soft deletes, uniques, and indexes work. It is meant to be approachable for contributors and readers new to both Maryk and FDB.

## TL;DR

- Every DataModel gets its own FDB directory (subspace) tree under a configurable root.
- We keep small, well‑known subspaces per model: `keys`, `table`, `unique`, `index`. If history is enabled, we also have `*_versioned` variants.
- “Latest” values live in `table` and are encoded as `(version || value)`. History (if enabled) lives in separate subspaces keyed by an inverted version suffix to keep “latest first” ordering.
- Soft deletes are object‑level flags stored as a special qualifier. Hard deletes clear all records (and history) for a key.
- Uniques and Indexes are first‑class citizens with current and (optionally) historic representations.

---

## Directory (Subspace) Layout

Per DataModel (e.g. `Log`, `Person`) we create these subspaces:

- `model`         – stored schema and migration state for the DataModel.
- `keys`          – existence + creation version per object key.
- `table`         – latest property values per object key, plus a small “latest version” marker per key.
- `unique`        – latest unique constraints (per unique property or composite).
- `index`         – latest secondary indexes.

If `keepAllVersions = true`, we also create historic subspaces:

- `table_versioned`  – all historical values per object key.
- `unique_versioned` – historic unique entries (tombstones and key snapshots).
- `index_versioned`  – historic index entries (tombstones and value snapshots).

All of the above are regular FDB subspaces created via the DirectoryLayer. They give us *prefix isolation* so we can read/write/scan per model efficiently.

## Keys and Qualifiers

Maryk uses a “row/column” style: the full FDB key is the subspace prefix + the Maryk key + a “qualifier” representing a property (or property+collection item).

Examples (pseudocode):

- Latest value for property: `tablePrefix + key + propertyQualifier` → `(version || value)`
- Object soft‑delete flag:  `tablePrefix + key + [0x00]`             → `(version || [0x01])`
- Creation timestamp:       `keysPrefix + key` → `version` (no value payload)
- Latest version marker:    `tablePrefix + key` → `version` (no qualifier; used to derive lastVersion)

The “qualifier” is generated from Maryk’s property references. Collections (list/set/map) qualify by index or element key. Embedded/object markers use small type markers.

## Value Encoding

- Latest values in `table`: stored as `(version || value)`
  - `version` is Maryk’s HLC timestamp (8 bytes, big‑endian). We use it for concurrency control, “last write wins”, and to expose `firstVersion`/`lastVersion` to clients.
- Historic values in `table_versioned`: stored on a separate key with inverted version bytes in the key suffix:
  - Key: `historicTablePrefix + key + qualifier + inverted(version)`
  - Value: just `value` (no version prefix, since it is already in the key)
  - Inverting bytes makes newer versions sort before older versions lexicographically, so a forward range scan gives “latest first”.

Maryk’s value serialization is reused across storage engines. Simple types are written directly, and for wrappers (e.g. enums, typed values) the inner type bytes are composed accordingly.

## Versioning

FoundationDB write transactions always write a new `version` (HLC timestamp) for any changed value(s). For `keepAllVersions = true` we mirror the write into the historic subspace using the inverted `version` in the key. Readers can then:

- Read the latest value from `table`.
- Read to a `toVersion` by scanning the historic subspace only up to the inverted `toVersion` (first match = latest ≤ `toVersion`).

## Soft Delete vs Hard Delete

- Soft delete sets the special “object delete” qualifier on the `table` (and historic if enabled), recording `(version || true)`.
- Hard delete clears:
  - `keysPrefix + key`
  - All `tablePrefix + key + ...`
  - All `table_versioned + key + ...` (if present)
  - And prunes related unique/index entries.

Consumers can request `filterSoftDeleted = true` and Maryk will transparently hide soft‑deleted objects.

## Uniques

Uniques are stored in `unique` as:

- Key:   `uniquePrefix + (uniqueRef || valueBytes)`
- Value: `(version || keyBytes)`

On insertion, we first read to check if a value already exists, and fail if the unique is taken. On update/delete we remove the unique entry (and, if historic is enabled, write a historic “tombstone” or snapshot into `unique_versioned`).

## Indexes

Indexes are stored in `index` as:

- Key:   `indexPrefix + indexRef + (indexValueBytes || keyBytes)`
- Value (latest): `version`
- Historic: `index_versioned + indexRef + (indexValueBytes || keyBytes) + inverted(version)` → empty value (tombstone) or snapshot

This design enables:

- Efficient scans in index order with or without a starting key.
- Partial prefix scans (e.g. “all rows for this severity”).
- Historic scans (if enabled) to see which keys matched an index at a given time. Note: historic index scanning is not fully implemented in the current FDB engine.

## Get, Scan, and Changes

- Get by key: check `keys` for existence and creation version, apply filters (including soft delete), then read values out of `table` or `table_versioned` depending on `toVersion`.
- Scan by key: compute key ranges from the model and filters, walk `keys` in ASC or DESC, apply filters, and collect up to `limit`.
- Scan by index: build index ranges from the filter and order, scan `index` (value+key) and map back to primary keys; historic index scanning is a future enhancement for the FDB engine.
- Changes APIs (GetChanges/ScanChanges): instead of returning full values, we stream `VersionedChanges` (creation + field changes) between `fromVersion`..`toVersion`, with `maxVersions` limiting per field.

## Filtering

Maryk’s filter DSL (Exists, Equals, Range, Regex, etc.) is evaluated by matching qualifiers to property references:

- For exact property references we do a direct get on `table` or scan `table_versioned` up to `toVersion`.
- For “fuzzy” references (like any element in a list/map) we scan the qualifier space under the property prefix. In the current FDB engine, `supportsFuzzyQualifierFiltering` and `supportsSubReferenceFiltering` are disabled to avoid expensive fan‑outs.

Soft delete filtering is layered in: if `filterSoftDeleted` is true, we check the soft delete indicator and hide those rows.

## Error Handling & Validations

All writes occur in a single FDB transaction per request. We do read‑for‑write validations (uniques, parent existence for nested values) inside the same transaction to avoid races. The transaction is retried by FDB on conflicts, and we propagate Maryk validation errors (e.g. unique violation) back to clients as structured responses.

## Why use separate historic subspaces?

Keeping current data in `table` and history in `table_versioned` gives us:

- small and fast lookups for “latest” queries;
- predictable scans for “as of” queries without mixing current and historic writes;
- forward‑only streaming (because of inverted version suffix) for time windows.

This maps neatly to FoundationDB’s range read strengths, while preserving Maryk’s model semantics.
