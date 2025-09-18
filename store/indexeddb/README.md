# Maryk IndexedDB Store

A Kotlin Multiplatform data store that persists Maryk models inside the browser's [IndexedDB](https://developer.mozilla.org/docs/Web/API/IndexedDB_API). It gives Kotlin/JS and Kotlin/Wasm applications the same version-aware APIs as the other Maryk engines, while remaining entirely client-side.

See also:
- [Architecture overview](docs/architecture.md)
- [Storage format](docs/storage.md)

## Getting Started

```kotlin

suspend fun openStore(): IndexedDBDataStore {
    return IndexedDBDataStore.open(
        databaseName = "maryk-demo",
        keepAllVersions = true,                // persist historic values as well
        dataModelsById = mapOf(
            1u to Account,
            2u to Course,
        ),
        fallbackToMemoryStore = true          // optional: keep working when IndexedDB is missing
    )
}

suspend fun example() {
    openStore().use { store ->
        store.execute(
            Account.add(
                Accoun.create { username with "demo"; password with "secret" },
            )
        )

        val result = store.execute(Account.scan())
        console.log("Stored accounts", result.values)
    }
}
```

Notes:
- The `databaseName` is the IndexedDB database; each model is stored in a separate object store named after `dataModel.Meta.name`.
- Always close the store (or use Kotlin's `use`) to flush pending writes and release the IndexedDB connection.
- Pass `fallbackToMemoryStore = true` when you want environments without IndexedDB (e.g. unit tests, old browsers) to fall back to an in-memory store. The default is `false`, which causes `open` to fail if IndexedDB is unavailable so you notice missing persistence early.

## Features

- All Maryk request types: Add, Change, Delete, Get, Scan, GetChanges, ScanChanges, and Update streaming.
- Optional historic retention (`keepAllVersions`) with replay support.
- Secondary indexes and unique constraints rebuilt on load and updated on every change.
- Automatic schema persistence and compatibility validation on open.

## Running Tests

Browser tests cover both JS and Wasm targets:

```bash
./gradlew store:indexeddb:jsBrowserTest --tests "maryk.datastore.indexeddb.IndexedDBDataStoreTest"
./gradlew store:indexeddb:wasmJsBrowserTest --tests "maryk.datastore.indexeddb.IndexedDBDataStoreTest"
```

These run the shared Maryk datastore conformance suite through the IndexedDB engine.

## Platform Support

This module targets Kotlin/JS (browser) and Kotlin/Wasm(JS, browser). JVM/native targets should use the other Maryk stores (`store/memory`, `store/rocksdb`, `store/foundationdb`).

