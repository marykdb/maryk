# Maryk IndexedDB Store

Persistent browser store for Maryk JS and WasmJS apps.

Targets:

- JS browser
- WasmJS browser

The store uses real IndexedDB object stores, keys, key ranges, transactions, and cursor scans. It does not execute requests by loading data into `InMemoryDataStore`.

## Use

Open the store with the data models used by the app:

```kotlin
val dataStore = IndexedDbDataStore.open(
    databaseName = "my-maryk-store",
    dataModelsById = mapOf(1u to Person),
    keepAllVersions = true,
    keepUpdateHistoryIndex = true,
)
```

Enable history only when the app needs `toVersion`, `getChanges`, `scanChanges`, or `scanUpdateHistory` semantics. History adds extra IndexedDB rows for snapshots, indexes, uniques, changes, and update-history scans.

## Documentation

- [Architecture](docs/architecture.md)
- [Storage layout](docs/storage.md)
- [Operations and query support](docs/operations.md)
- [Migrations, encryption, and testing](docs/migrations-encryption-testing.md)

## Main Capabilities

- Current `add`, `change`, `delete`, `get`, `scan`, `getUpdates`, `scanUpdates`.
- Table scans over IndexedDB key cursors.
- Index scans over Maryk-encoded secondary index rows.
- Unique lookups over direct IndexedDB gets.
- Historic `toVersion` get/table/index/unique reads when `keepAllVersions` is enabled.
- `getChanges`, `scanChanges`, and `scanUpdateHistory` over durable history rows.
- Sensitive value encryption through the shared Maryk field encryption provider API.
- Browser-native WebCrypto AES-GCM/HMAC-SHA256 provider for JS and WasmJS.
- Metadata-backed startup migration with safe-add handling and index backfill.
- JS and WasmJS test coverage through `fake-indexeddb`.

## Important Limits

- IndexedDB transactions cannot safely wrap the whole suspend common request processor. Mutations are serialized with Web Locks when available, then written through native batched `readwrite` transactions.
- Without Web Locks, mutation serialization is only in-process for the current JS context.
- Historic index scans process IndexedDB cursor pages, but still keep latest visible state per indexed row to handle historic tombstones correctly.
- Browser quota, eviction, private browsing behavior, and blocked upgrades are browser operational concerns.

## Tests

Focused checks:

```bash
./gradlew :store:indexeddb:jsNodeTest
./gradlew :store:indexeddb:wasmJsNodeTest
```

Browser checks when available:

```bash
./gradlew :store:indexeddb:jsBrowserTest
./gradlew :store:indexeddb:wasmJsBrowserTest
```
