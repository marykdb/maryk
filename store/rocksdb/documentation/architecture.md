# Maryk RocksDB Store: Architecture

This document explains how the RocksDB-backed data store is structured and how it maps Maryk’s versioned data model onto column families, keys, and values. It is written for contributors who want to understand the implementation.

- Module: `store/rocksdb`
- Key entry points:
  - `RocksDBDataStore` – lifecycle, request loop, migrations, column family setup
  - `processors/*` – request handlers (Add/Change/Delete/Get/Scan/Updates)
  - `processors/helpers/*` – low-level encoding/decoding and KV helpers
  - `TableType`, `*TableColumnFamilies` – column family descriptors and handles
  - `Transaction`, `DBAccessor`, `DBIterator` – local write buffering and reads

## High-Level Overview

Maryk stores each DataModel in a set of RocksDB column families. Current (“latest”) values live in `table`, while keys, uniques, and indexes have their own families. When `keepAllVersions = true`, history is written into separate `historic` families with a custom comparator to keep “latest first” ordering per key.

All higher‑level request semantics (validation, update streaming, filtering, aggregates) are shared across engines and implemented in `store/shared`. The RocksDB module focuses on low‑level persistence and iteration.

## Column Families per Model

For every registered DataModel (identified by `UInt` in `dataModelsById`), we create these column families:

- `Model` – stored schema and migration state for the model.
- `Keys` – existence + creation version per object key.
- `Table` – latest property values per object, plus a “latest version” marker.
- `Index` – latest secondary index entries.
- `Unique` – latest unique constraint entries.

If `keepAllVersions = true`:

- `HistoricTable` – all historical values per object.
- `HistoricIndex` – historical index entries.
- `HistoricUnique` – historical unique entries.

Column family names are compact byte arrays: first byte is the `TableType` (1..8), followed by the varint-encoded model id. See `TableType.getDescriptor`.

RocksDB options:
- `Table` and `HistoricTable` use a fixed-length prefix extractor sized to the model key for efficient key‑range scans.
- `Historic*` families use `VersionedComparator`, which sorts by key/qualifier first and version second, so qualifiers stay grouped.

## Keys, Qualifiers, and Values

Maryk uses a row/column style encoding. The primary key (Maryk `Key`) is followed by a qualifier that identifies the property (and collection item if applicable).

Latest data, in `Table`:
- Object creation: `table[key] = version`
- Latest version marker: `table[key || [LAST_VERSION_INDICATOR=0b1000]] = version`
- Soft delete flag: `table[key || [SOFT_DELETE_INDICATOR=0x00]] = (version || [0x01])`
- Property value: `table[key || qualifier] = (version || value)`

Creation version, in `Keys`:
- `keys[key] = version`

Historic data (when enabled):
- `historicTable[key || qualifier || inverted(version)] = value`
  - The version is stored in the key suffix with all bytes inverted to make newer versions sort first (forward scans yield latest‑first). See `toReversedVersionBytes`.

Versions are 8‑byte HLC timestamps. Helper functions live in `processors/helpers/VersionBytesConverters.kt`.

## Indexes and Uniques

- Index (latest):
  - Key:   `index[indexRef || indexValueBytes || keyBytes]`
  - Value: `version`
- Index (historic):
  - Key:   `historicIndex[indexRef || indexValueBytes || keyBytes || inverted(version)]`
  - Value: empty

- Unique (latest):
  - Key:   `unique[uniqueRef || valueBytes]`
  - Value: `(version || keyBytes)`
  - Unique names are enumerated by writing a single marker with a 0x00 prefix; see `createUniqueIndexIfNotExists` and `getUniqueIndices`.
- Unique (historic):
  - Key:   `historicUnique[uniqueRef || valueBytes || inverted(version)]`
  - Value: `keyBytes`

Helpers for writing/erasing these entries are in `processors/helpers/*IndexValue.kt`.

## Request Flow

`RocksDBDataStore.startFlows()` runs a coroutine actor that receives requests and dispatches to processors:

- Add → `processAddRequest` → validates values, writes `keys/table`, uniques, indexes, and emits an `Addition` update.
- Change → `processChangeRequest` → reads current values as needed, updates values and affected indexes/uniques, emits `Change`.
- Delete → `processDeleteRequest` → either hard-deletes all KV for the key (plus history) or writes a soft‑delete flag; emits `Removal`.
- Get/Scan → `processGetRequest` / `processScanRequest` → read values from `table` or `historicTable` depending on `toVersion`; use `Keys` to iterate keys efficiently; optional aggregation via `Aggregator`.
- GetChanges/ScanChanges/Updates → stream versioned changes based on `fromVersion`..`toVersion` using historic storage when available.

Common read helpers:
- `DBAccessorStoreValuesGetter` decodes values on demand and can capture the highest seen version while reading.
- `getValue()` selects latest versus historic storage based on `toVersion`.

## Transactions and Concurrency

Writes are buffered in an in-process `Transaction` (not a RocksDB transaction). It records puts/deletes per column family and performs optimistic checks at commit time (`getForUpdate` + compare on commit) to detect conflicting changes.

- `Transaction.put/delete` enqueue changes.
- `commit()` performs pre-commit reads to ensure rows were not modified concurrently, then applies changes in column family order.
- Point reads during a transaction are resolved from staged changes first, falling back to RocksDB.

This local transaction keeps the implementation portable across JVM and Native targets while providing a simple “read-your-writes” view within a single request.

## Migrations and Version Updates

At open time, `RocksDBDataStore` compares the stored model in `Model` with the provided definition:
- If models are equal → no work.
- If only safe additions or new indexes exist → it backfills index data (`fillIndex`) and stores the new model.
- If incompatible changes are detected → it calls `migrationHandler`; the handler can perform corrective writes and must return:
  - `MigrationOutcome.Success`
  - `MigrationOutcome.Partial`
  - `MigrationOutcome.Retry`
  - `MigrationOutcome.Fatal`

After a successful migration or a brand new model, `versionUpdateHandler` can run custom logic (e.g., seed data) before the new model is stored.

See: `model/checkModelIfMigrationIsNeeded.kt` and `model/storeModelDefinition.kt`.

Runtime operations:
- `migrationStartupBudgetMs` + `continueMigrationsInBackground` support startup handoff to background migration.
- Pending models are request-blocked via `assertModelReady`.
- Operators can inspect/control with `pendingMigrations`, `migrationStatus(es)`, `awaitMigration`, `pauseMigration`, `resumeMigration`, `cancelMigration`.
- Default lease is local (`RocksDBLocalMigrationLease`) to avoid duplicate runners inside one process.

## Configuration Surface

Open a store with:

```kotlin
RocksDBDataStore.open(
    keepAllVersions = true,
    relativePath = "path/to/db",
    dataModelsById = mapOf(1u to Account, 2u to Course),
    rocksDBOptions = DBOptions(),
    migrationHandler = { context -> /* ... */ MigrationOutcome.Success },
    versionUpdateHandler = { store, stored, new -> /* ... */ }
)
```

Notes:
- `keepAllVersions` controls whether historic families are created and maintained.
- `relativePath` points to a directory where the store manages all column families.
- `dataModelsById` determines the model ids used in column family names and writes.
- Pass custom `DBOptions` and `ColumnFamilyOptions` via `openRocksDB` if you need to tune cache, compaction, bloom filters, etc.

## Testing

The `store/test` module contains a reusable test suite that the RocksDB store runs in `commonTest`. It covers adds/changes/deletes, uniques, indexes, scans, and historic queries. See `RocksDBDataStoreTest.kt` and related tests in `src/commonTest`.

## File Map (selected)

- `RocksDBDataStore.kt` – open/init, CF creation, request actor, index backfill, close.
- `TableType.kt` – column family naming (type byte + varint id).
- `BasicTableColumnFamilies.kt`, `TableColumnFamilies.kt`, `HistoricTableColumnFamilies.kt` – handles per model.
- `processors/*` – per-request processing and scan logic.
- `processors/helpers/*` – version bytes, get/set value/index/unique, soft delete checks.
- `Transaction.kt`, `DBAccessor.kt`, `DBIterator.kt` – local transaction and read wrappers.
