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
- All column families share an explicitly owned LRU cache. Default-owned DB options also attach a shared write-buffer manager and statistics; caller-supplied DB options retain ownership of those settings. `RocksDBTuning` provides resource budgets without changing the existing `open` signature.
- Maintenance iterators and record reads disable cache filling. Request snapshots retain normal cache behavior. Eligible bytewise prefix predicates use scoped native iterator bounds, while historic comparator predicates keep their existing boundary checks.
- Checked iterators report native status on exhaustion and before closing. Diagnostics expose approximate memory accounting, cache counters, compaction backlog and write stalls; checksum verification runs on the IO dispatcher.
- Column families use LZ4 compression, with Zstandard for the bottommost level. Each existing SST retains its own codec metadata; settings apply to new flush/compaction output without rewriting files at open. The bindings retain the codecs needed to read older Snappy and uncompressed files.
- `Table` and `HistoricTable` use a fixed-length prefix extractor sized to the model key for efficient key‑range scans.
- `Historic*` families use `VersionedComparator`, which sorts by key/qualifier first and version second, so qualifiers stay grouped.
- Presence-only index and migration-marker checks use exact `keyExists` reads, avoiding value copies. Transactional checks retain transaction reads for pending-write visibility, conflict tracking, and atomic work accounting. `keyMayExist` is not treated as proof of presence.
- Request contexts share one immutable, snapshot-bound total-order read option for point reads and scans. Readers enforce their record, key and prefix boundaries explicitly; store-level maintenance readers can retain prefix-restricted options.
- Transactional range/prefix deletion uses total-order iterators and checks iterator status before staging each bounded batch of deletes. This includes pending writes and supports savepoint rollback without relying on non-transactional range tombstones.

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

Current projections of up to 16 simple root properties skip unselected collection
subtrees with seeks on the existing request iterator. This avoids walking every
collection item to return a few metadata fields. Missing fields, soft deletion,
selected-field versions, snapshots and pending transaction writes retain their
ordinary semantics. Small scalar models, full records, complex selections and
historic snapshots use the existing sequential decoder. Historical projections
also inspect unselected qualifiers to preserve their visible `lastVersion`.

Historical point reads reuse a supplied request reader for both present and absent
values. A missing/deleted value or a decoder returning null does not create a
second iterator or invoke the decoder again. Reader ownership stays with its caller.

Older stores can have a current soft-delete marker without its historic row.
The change-reader fallback uses that marker only when its timestamp lies within
the inclusive requested version window. Finite update history narrows this window
to the single indexed revision, so a later deletion or restoration cannot consume
an older revision's page limit or become its continuation boundary.

For snapshot state and filters, a current marker whose timestamp is at or before
the requested snapshot overrides an older historic marker. This applies to both
deletion and restoration; a later marker cannot change an earlier snapshot.
Change readers merge a recovered current transition with existing history and
retain at most `maxVersions` soft-delete transitions, independently of other
properties. Active-record history still includes a restoration transition when
its historic row is missing.

## JVM historic comparator

On matching macOS, Linux (including Alpine/musl) and Windows x64 JNI builds, a small native comparator avoids JVM
callbacks during historic comparisons. It retains the `maryk.VersionedComparator`
name and ordering, including short metadata keys. Unsupported platforms, model
keys shorter than eight bytes, missing resources and mismatched native binaries
use the common implementation. No storage migration is needed.

The loader verifies the active JNI image against its build manifest. Unix libraries
link to that same image. The Windows DLL is a standalone MSVC-compatible subclass:
it uses the matching RocksDB headers and shared Microsoft runtime, with local
overrides for its option-free configuration behavior. It links no RocksDB engine
or support implementation and leaves the Java JNI DLL unchanged. In the build's
header copy, `novtable` suppresses the unused `Configurable` base vtable without
changing its members or virtual slots. Only the complete concrete comparator is
instantiated. Native resources are built against checksum-pinned RocksDB
headers. Gradle builds and verifies the resources directly using a C/C++ compiler;
Python is not required. Without a compiler, the JVM build uses the common comparator.
Gradle skips unchanged local builds using declared source, JNI and JDK inputs and
generated resource outputs. Shared build-cache reuse is disabled because native
outputs depend on the host compiler and SDK. Offline builds use the common
comparator when the source archive is not cached. To build the native comparator
offline, provide the verified archive with
`-Pmaryk.rocksdbNativeSourceArchive=/path/to/archive`. Supplied archives and native
bundles still require valid checksums; invalid inputs fail the build. Fallback
builds retry preparation on the next invocation so an available archive can enable
native output. Dedicated native CI jobs continue to require the native comparator.
Publication assembles tested platform resources from the successful Build run
for the exact source commit and verifies their inputs and checksums again.

### Cross-compilation on macOS

The JVM bundles are macOS ARM64/x64, Linux glibc ARM64/x64, Linux musl ARM64/x64
and Windows x64. Windows ARM64 retains the common comparator.

For compilation without running foreign code, install LLVM/LLD and the musl
cross-compilers, and prepare Kotlin/Native's Linux sysroots:

```bash
brew install llvm lld FiloSottile/musl-cross/musl-cross
./gradlew :store:rocksdb:compileKotlinLinuxX64 :store:rocksdb:compileKotlinLinuxArm64
LLVM_LINKER="$(brew --prefix lld)/bin/ld.lld" \
  WINDOWS_SDK_ROOT=/path/to/msvc-sdk \
  WINDOWS_LINKER="$(brew --prefix lld)/bin/lld-link" \
  bash store/rocksdb/scripts/compile-jvm-comparator.sh all --offline
```

The offline command requires the verified RocksDB source archive to be cached
or supplied through `-Pmaryk.rocksdbNativeSourceArchive=/path/to/archive`.
Pass one platform instead of `all` to compile only that bundle. `LLVM_BIN`,
`LLVM_LINKER`, `MUSL_CROSS_BIN` and `KONAN_DATA_DIR` can select existing toolchains.
Unix targets reuse the host JDK's JNI declarations, whose primitive sizes and
calling convention match these 64-bit Unix targets.

For Windows, prepare Microsoft's headers and import libraries with
[xwin](https://github.com/Jake-Shadle/xwin), then set `WINDOWS_SDK_ROOT` to its
output directory. CI uses xwin 0.6.5, SDK 10.0.22621 and CRT 14.44.17.14:

```bash
xwin --accept-license --arch x86_64 --sdk-version 10.0.22621 \
  --crt-version 14.44.17.14 splat --output /path/to/msvc-sdk
WINDOWS_SDK_ROOT=/path/to/msvc-sdk \
  bash store/rocksdb/scripts/compile-jvm-comparator.sh windows-x86_64
```

Clang targets `x86_64-pc-windows-msvc`, not MinGW, with the shared release CRT
and iterator debugging disabled. The host JDK's platform-neutral `jni.h` uses
the small Windows x64 platform definitions in `src/jvmNative/jni-windows`.

Outputs are isolated under `build/generated/jvmNativeComparatorCrossResources/<platform>`.
These are compile results until tested: CI cross-compiles the additional Linux
and Windows bundles on macOS, then downloads and tests those exact files on ARM64
Linux, architecture-matched Alpine containers or Windows. Tests require native loading,
compare ordering with the common implementation, verify flush/reopen and OPTIONS
serialization through the JNI engine, and run the RocksDB module suite.
Only successful test jobs upload resources under the publication artifact names.
The publication verifier requires all seven platform bundles.

## Transactions and Concurrency

The store opens RocksDB as an `OptimisticTransactionDB`. Maryk keeps a small `Transaction` adapter around the native RocksDB transaction API so processors can use the same request-local access pattern while commits are handled by RocksDB atomically.

- `Transaction.put/delete` write into the native transaction batch.
- `getForUpdate()` delegates to RocksDB optimistic conflict tracking.
- `commit()` commits the native transaction atomically and starts a fresh native transaction for callers that process multiple objects in one request.
- Point reads and iterators during a transaction are provided by RocksDB and include pending writes.
- Creation-version probes use buffer-based point reads, preserving request order, duplicates, snapshot visibility, legacy historic-marker fallbacks, and transaction conflict/work accounting. Warm small-value probes did not justify batching on Native; stored metadata pairs still use `MultiGet` for coherent reads.

This keeps the implementation portable across JVM and Native targets while relying on RocksDB for atomic commit, rollback/savepoints, read-your-writes, and optimistic conflict detection.

Maryk's public `transaction` scope is separate from this native adapter. Each
preview replays its journal in a fresh native transaction and closes without
publication. A datastore revision key protects reads and predicates across
previews; commit publishes the complete journal and retry receipt together.
Application suspension holds no native transaction open. `AtomicRequest` uses
the same engine with initial-state checks. See the
[shared transaction contract](../../../core/docs/query.md#serializable-transactions).

## Migrations and Version Updates

Operational details and full runtime API are documented in [Migrations](./migrations.md).

Stored model definitions, dependency payloads, and storage-format flags are read through one scoped RocksDB snapshot; model/dependency payloads are fetched together with `MultiGet`.
Migration progress and target identity are also fetched together through scoped
snapshot read options, which are closed before the snapshot is released.

At open time, `RocksDBDataStore` compares the stored model in `Model` with the provided definition:
- If models are equal → no work.
- If only safe additions or new indexes exist → it backfills index data (`fillIndex`) and stores the new model.
- If incompatible changes are detected → it calls `migrationConfiguration.migrationHandler`; the handler can perform corrective writes and must return:
  - `MigrationOutcome.Success`
  - `MigrationOutcome.Partial`
  - `MigrationOutcome.Retry`
  - `MigrationOutcome.Fatal`

Pass these settings under `migrationConfiguration = MigrationConfiguration(...)` from `maryk.core.models.migration`.

After a successful migration or a brand new model, `versionUpdateHandler` can run custom logic (e.g., seed data) before the new model is stored.

See: `model/checkModelIfMigrationIsNeeded.kt` and `model/storeModelDefinition.kt`.

Runtime operations:
- `migrationConfiguration.migrationStartupBudgetMs` + `migrationConfiguration.continueMigrationsInBackground` support startup handoff to background migration.
- Optional phase hooks also live under `migrationConfiguration`: `migrationExpandHandler`, `migrationVerifyHandler`, `migrationContractHandler`.
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
    migrationConfiguration = MigrationConfiguration(
        migrationHandler = { context -> /* ... */ MigrationOutcome.Success }
    ),
    versionUpdateHandler = { store, stored, new -> /* ... */ }
)
```

Notes:
- `keepAllVersions` controls whether historic families are created and maintained.
- `relativePath` points to a directory where the store manages all column families.
- `dataModelsById` determines the model ids used in column family names and writes.
- Pass custom `DBOptions` and `ColumnFamilyOptions` via the datastore open path if you need to tune cache, compaction, bloom filters, etc.

## Testing

The `store/test` module contains a reusable test suite that the RocksDB store runs in `commonTest`. It covers adds/changes/deletes, uniques, indexes, scans, and historic queries. See `RocksDBDataStoreTest.kt` and related tests in `src/commonTest`.

## File Map (selected)

- `RocksDBDataStore.kt` – open/init, CF creation, request actor, index backfill, close.
- `TableType.kt` – column family naming (type byte + varint id).
- `BasicTableColumnFamilies.kt`, `TableColumnFamilies.kt`, `HistoricTableColumnFamilies.kt` – handles per model.
- `processors/*` – per-request processing and scan logic.
- `processors/helpers/*` – version bytes, get/set value/index/unique, soft delete checks.
- `Transaction.kt`, `DBAccessor.kt`, `DBIterator.kt` – local transaction and read wrappers.

### Complete-value checks and empty collection removal

Check statements decode scalar qualifiers directly and materialize complex values
once from the current native transaction. A missing expected scalar fails validation;
collection counts are never compared to whole collection values. Checks after a
change see the staged writes, and validation failure rolls back the record savepoint.
Root list/set/map counts have no type indicator: a zero-count byte means a present,
empty collection. Nullable removal deletes that qualifier and its descendants,
including the empty case, and retains the normal history tombstones.

## Physical backup and recovery

`RocksDBBackupRepository` wraps the engine backup APIs in serialized IO operations.
It embeds `MARYK_META.yml` and history settings in each backup's application
metadata, prevents overlap with database directories, and restores only into new
directories. A store lifecycle guard keeps native resources alive during backup;
schema/index migrations must finish before capture. See
[backup and restore](backups.md) for how physical recovery complements the shared
portable record backup format.
