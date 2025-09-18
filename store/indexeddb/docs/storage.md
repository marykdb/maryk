# IndexedDB Storage Layout

Maryk’s IndexedDB engine stores each record as a separate IndexedDB entry. Every object store holds JSON documents describing individual records, their versions, and historic values so browsers can persist them without custom codecs. This document describes that layout so you can inspect the IndexedDB database or extend the persistence layer.

## Database and Object Stores

- Database name – provided via `IndexedDBDataStore.open(databaseName = …)`.
- Object store name – the Maryk model name (`dataModel.Meta.name`). One object store per model.
- Primary key – Maryk record key encoded as base64. Each key/value pair represents a single persisted record.
- The `fallbackToMemoryStore` option (see README) keeps the same layout but stores it in memory instead of the browser database when IndexedDB is not available.

If the environment does not expose IndexedDB (e.g. NodeJS unit tests) the engine falls back to an in-memory map but uses the same shape.

## PersistedRecord Document

```json
{
  "key": "<base64 primary key>",
  "firstVersion": "<hlc timestamp as unsigned decimal>",
  "lastVersion": "<hlc timestamp as unsigned decimal>",
  "values": [ /* PersistedNode */ ]
}
```

- `key` – Maryk Key bytes encoded with URL-safe base64. It duplicates the IndexedDB primary key so the document is self-describing when exported.
- `firstVersion` / `lastVersion` – Hybrid Logical Clock timestamps stored as decimal strings (`ULong.toString()`), matching Maryk’s other stores.
- `values` – ordered by qualifier (also base64). The array may contain two node types.

## PersistedNode

### Value node (latest entry)

```json
{
  "kind": "value",
  "reference": "<base64 qualifier>",
  "version": "<hlc timestamp>",
  "valueJson": "{\"firstName\":\"Ada\"}",
  "isDeleted": false
}
```

- `valueJson` is the Maryk JSON representation of the property value. It may be `null` when `isDeleted` is `true`.
- `isDeleted = true` marks soft-deleted values (or collection elements) while keeping the version for time-travel queries.

### Historic node

```json
{
  "kind": "historic",
  "reference": "<base64 qualifier>",
  "history": [
    { "version": "…", "valueJson": "…", "isDeleted": false },
    { "version": "…", "valueJson": null, "isDeleted": true }
  ]
}
```

Historic nodes appear only when the datastore is opened with `keepAllVersions = true`. Each entry in `history` uses the same shape as a `value` node but omits the redundant `kind`/`reference` fields. Entries are ordered from oldest to newest; the Kotlin side appends newer versions on commit.

## Indexes and Uniques

Index and unique metadata is **not** stored explicitly. During deserialisation the engine rebuilds them by iterating over the records and re-computing index keys via the model definitions (`rebuildIndexesForRecord`). This keeps the persisted payload smaller and ensures new indexes can be backfilled on load.

## Example Entry

A minimal example for a model with one string property is stored under the primary key `"AQ=="`:

```json
{
  "key": "AQ==",
  "firstVersion": "18446744073709551616",
  "lastVersion": "18446744073709551616",
  "values": [
    {
      "kind": "value",
      "reference": "AAE=",
      "version": "18446744073709551616",
      "valueJson": "\"demo\"",
      "isDeleted": false
    }
  ]
}
```

Decoded:
- primary key `0x01`
- qualifier `0x0001`
- value `"demo"` with the recorded version

## Cleaning up Stores

`IndexedDBDataStore` does not automatically delete model object stores. Dropping a model from `dataModelsById` leaves the data untouched; you can call `deleteStore(storeName)` on the driver (currently unused) or clear the store via browser dev tools if needed.

Understanding this structure helps when inspecting data with the browser’s IndexedDB viewer or when writing migration scripts outside of the Kotlin runtime.
