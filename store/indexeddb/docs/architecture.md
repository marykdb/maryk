# Maryk IndexedDB Store: Architecture

This document explains how the browser-oriented IndexedDB engine turns Maryk's generic datastore abstractions into IndexedDB calls. It is intended for contributors who want to navigate or extend the implementation.

## Module Overview

- Location: `store/indexeddb`
- Primary entry point: [`IndexedDBDataStore`](../src/commonMain/kotlin/maryk/datastore/indexeddb/IndexedDBDataStore.kt)
- Supporting packages:
  - `records/` – in-memory representation of objects, indexes, and historic values
  - `processors/` – request handlers shared with other stores (add/change/delete/scan/...)
  - `persistence/` – serializers that transform `DataStore` content to a persistable DTO and back
  - platform-specific drivers under `src/jsMain` and `src/wasmJsMain`

The module keeps all data in a process-local `DataStore` (an ordered list of `DataRecord`s plus index structures). IndexedDB is used as a persistence backing: each Maryk record is serialized to its own object store entry, and after every successful mutation we rewrite the persisted set so the browser copy mirrors the in-memory snapshot.

## Lifecycle

1. **Open** — `IndexedDBDataStore.open` receives a database name, model map, and `keepAllVersions` flag. For each model it asks the platform driver to load a persisted snapshot (if any) and converts it into a `DataStore` via `PersistedDataStore.toDataStore`.
2. **Actor loop** — Like other Maryk stores, we reuse `AbstractDataStore` from `store/shared`. A coroutine actor listens for incoming requests and dispatches to the processors. The processors mutate `DataRecord`s and update the in-memory indexes/uniques.
3. **Persist** — After every mutation-producing request and after consuming update streams we call `driver.writeStore`, serializing the affected `DataStore` through `toPersistedDataStore`. Reads do not hit IndexedDB; they operate on the live in-memory snapshot.
4. **Close** — `IndexedDBDataStore.close` completes the actor and delegates to the driver, which closes the browser connection (or clears the in-memory fallback).

Because IndexedDB I/O is relatively expensive, persistence happens at request granularity. Within a request all reads/writes go through the `DataStore`, giving consistent read-your-writes semantics before the next persistence flush.

## Serialization Pipeline

`store/indexeddb/persistence` holds two value types:

- `PersistedDataStore` – a DTO containing a list of `PersistedRecord`s.
- `PersistedRecord` – stores base64-encoded keys, HLC versions as strings, and a list of `PersistedNode`s.

`PersistedNode` has two shapes:

- `PersistedValueNode` for latest values or individual timeline entries (reference bytes, version, JSON payload, deletion flag).
- `PersistedHistoricNode` for a property with historic entries (`history` is a list of `PersistedValueNode`s).

The serializer uses Maryk's JSON codecs to convert property values to a `String` (`valueJson`) so that polymorphic values round-trip without schema-specific code. Binary identifiers (keys, qualifiers, index references) are stored as base64 strings.

During load we rebuild indexes and unique constraints by replaying the records (`rebuildIndexesForRecord`). This keeps the persisted format compact (only the primary data is stored) and guarantees index definitions added later can be backfilled from the values.

## Platform Driver

`IndexedDbDriver` encapsulates IndexedDB access with three operations: `loadStore`, `writeStore`, and `deleteStore`.

- It resolves `globalThis.indexedDB` (including vendor prefixes) via a small `@JsFun` helper. When available, each Maryk model is stored in its own object store named after `dataModel.Meta.name`. Every persisted record is stored as a separate JSON document keyed by the base64 encoded Maryk key, allowing the store to iterate over entries with IndexedDB cursors.
- When IndexedDB is not present and `fallbackToMemoryStore = true`, the driver switches to `InMemoryIndexedDbDriver`, which now keeps a map of persisted records per store for the lifetime of the process.

All value encoding/decoding happens in Kotlin, so behaviour stays identical across JS and Wasm builds.

## Request Processing

All request processors are reused from `store/shared`. The notable IndexedDB-specific aspects are:

- `DataStore` maintains sorted record lists (`records`), index entries (`IndexValues`), and unique constraints (`UniqueIndexValues`). Binary search with the shared `compareTo` extensions keeps lookups efficient despite being in-memory.
- Historic values are optional. When `keepAllVersions = true` `DataRecordHistoricValues` retains timelines per qualifier; otherwise only the latest value is kept.
- After each mutation we call `DataRecordNode.commit()` so that lazily appended historic values are materialised before serialization.

## Schema Management

On open, the store uses the same model checking as other engines: the stored model definition is compared against the provided model, and any change that requires a migration will trigger the supplied `migrationHandler`. Because IndexedDB persistence is a snapshot, migrations generally resemble the memory store – handlers issue Maryk requests against the in-memory store, and the subsequent persistence flush writes the new state back to IndexedDB.

## Fallback Behaviour

When running unit tests (`jsNodeTest`, `wasmJsNodeTest`) or in environments without IndexedDB, `findIndexedDbFactory()` returns `null`. The behaviour depends on the `fallbackToMemoryStore` flag passed to `IndexedDBDataStore.open`:

- `fallbackToMemoryStore = false` (default) – opening the store fails immediately with an explanatory exception. This is useful when persistence must be present and you want misconfigured environments to surface quickly.
- `fallbackToMemoryStore = true` – the driver returns an `InMemoryIndexedDbDriver`. It fulfils the same contract but keeps the persisted records in a map for the lifetime of the process. This keeps test environments or legacy browsers working, albeit without durable storage.

Both flows reuse the same serialization logic, so promoting an in-memory session to real IndexedDB requires only toggling the flag and re-opening the store.

## File Map (selected)

- `IndexedDBDataStore.kt` – open/persist lifecycle and request actor integration.
- `IndexedDbDriver.kt` (jsMain/wasmJsMain) – bridge to IndexedDB APIs or in-memory fallback.
- `persistence/DataStorePersistence.kt` – conversion between in-memory records and persisted DTOs.
- `records/DataStore.kt` – in-memory record/index management used by processors.
- `processors/*` – shared request implementations specialised for the IndexedDB data structures.

Understanding these pieces should make it straightforward to extend the engine, e.g. by optimising persistence batching, adding encryption, or extending the stored metadata.
