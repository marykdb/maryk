# IndexedDB Store Exploration

This is the original exploration report. For current module documentation, use:

- `store/indexeddb/README.md`
- `store/indexeddb/docs/architecture.md`
- `store/indexeddb/docs/storage.md`
- `store/indexeddb/docs/operations.md`
- `store/indexeddb/docs/migrations-encryption-testing.md`

Goal: add a browser store for JS and WasmJS that uses IndexedDB directly. No memory-store fallback for query execution.

## Summary

Feasible.

Maryk stores already fit an ordered key-value engine. IndexedDB supports binary keys, key ranges, and forward/reverse cursors, so a browser engine can map Maryk's RocksDB/FoundationDB layout to object stores and keep scan logic over IndexedDB cursors.

Best target:

- `store:indexeddb` Kotlin Multiplatform module.
- Browser-only JS and WasmJS support.
- No NodeJS support unless tests inject a fake IndexedDB implementation.
- Shared high-level behavior from `store/shared`.
- Engine-specific cursor adapter modeled after RocksDB `DBAccessor`/`DBIterator`, but async.

Verdict on query coverage: all current datastore request types can be supported. Some historic index scans need bounded in-query state to collapse visible versions per key, same as current RocksDB logic. That is not a memory-store fallback, but it is not purely streaming either.

Current branch status:

- New `:store:indexeddb` module exists.
- JS and WasmJS targets use real IndexedDB wrappers; tests inject `fake-indexeddb`.
- Current-state data uses separate key/table/index/unique object stores.
- A second history shape now exists: `c:<modelId>` stores one durable change row per `key + version`.
- A third update-history shape now exists when enabled: `uh:<modelId>` stores inverted `version + key` rows for newest-first update history scans.
- Finer historic stores now exist when `keepAllVersions` is enabled:
  - `ht:<modelId>` stores `key-scope + inverted version -> meta + table rows`.
  - `hi:<modelId>` stores `index ref + index value + key + inverted version -> active marker`.
  - `hu:<modelId>` stores `unique ref + value + inverted version -> key or tombstone`.
- Current table, change-log, and historic snapshot rows use a length-prefixed object-key scope before row suffixes. This prevents IndexedDB prefix scans for one key from matching another key with the same raw byte prefix.
- Request handling is implemented for add, change, delete, get, get changes, get updates, process update, table scan, table scan changes, table scan updates, scan update history, index scan, index scan changes, and unique scan fast path.
- Historic `toVersion` get/table/index/unique scans read those finer stores through IndexedDB cursor scans.
- Hard deletes purge current table rows, change-log rows, historic snapshots, historic index rows, historic unique rows owned by the deleted key, and update-history rows for the deleted key.
- Change requests validate changed values before storage rows are rewritten.
- No request falls back to `InMemoryDataStore`.

## Browser API Fit

IndexedDB key ordering is good enough for Maryk byte layouts:

- Binary keys compare byte-wise as unsigned bytes.
- `IDBKeyRange` gives inclusive/exclusive lower and upper bounds.
- Cursors support increasing and decreasing order with `next` and `prev`.
- Requests are async and transaction-scoped.

Spec references:

- IndexedDB key ranges: https://www.w3.org/TR/IndexedDB/#key-range
- IndexedDB binary key ordering: https://www.w3.org/TR/IndexedDB/#key-construct
- Cursor direction: https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/openCursor

Main caveat: every low-level API call is async. Existing RocksDB/FDB processor code is synchronous around iterator movement. A direct port needs suspend wrappers and async cursor loops instead of pretending IndexedDB is blocking.

## Proposed Storage Layout

One IndexedDB database per Maryk store name.

One object store per Maryk table family:

| Maryk family | IndexedDB object store | Key | Value |
| --- | --- | --- | --- |
| model | `m:<modelId>` or global `model` | model metadata key | serialized model bytes |
| keys | `k:<modelId>` | Maryk record key bytes | empty marker/latest version marker |
| table | `t:<modelId>` | key-scope + property qualifier | value bytes |
| index | `i:<modelId>` | index ref + encoded index value + key | marker bytes |
| unique | `u:<modelId>` | unique ref + encoded unique value | key bytes |
| change log | `c:<modelId>` | key-scope + version | versioned change bytes |
| update history | `uh:<modelId>` | inverted version + key | versioned change bytes |
| historic table | `ht:<modelId>` | key-scope + inverted version | meta + value rows snapshot |
| historic index | `hi:<modelId>` | index ref + encoded index value + key + inverted version | active marker |
| historic unique | `hu:<modelId>` | unique ref + encoded value + inverted version | key or tombstone |

Alternative: one object store per family with compound key `[modelId, Uint8Array]`. Avoid this. Maryk needs byte-exact ordered prefixes. Native binary keys in separate stores map closer to the existing layout.

The second shape is worth keeping. Current-state rows are optimized for latest reads and scans. Change rows are optimized for `GetChanges` by key/version. Finer historic table/index/unique rows are needed for native `toVersion` scans and index/unique visibility without replaying the change log.

## Required Building Blocks

1. `IndexedDbDataStore`

Use `AbstractDataStore`, same actor/request flow as existing engines.

2. Async accessor

Create an IndexedDB-native equivalent of:

- `get(store, key): ByteArray?`
- `put(store, key, value)`
- `delete(store, key)`
- `openCursor(store, range, direction)`
- `seek(start)`
- `next()`
- `prev()` through cursor direction and explicit re-open when needed.

Do not implement a full in-memory iterator. Cursor steps should read from IndexedDB.

3. Transaction runner

Each Maryk read/scan uses native IndexedDB readonly requests over the required object stores. Each mutation serializes the whole logical request with a browser lock, then commits the planned writes through one native `readwrite` batch transaction.

Current implementation note: add/change/delete mutations run inside an exclusive Web Locks API scope when available, then commit writes through one native IndexedDB `readwrite` transaction in both JS and WasmJS. This serializes preflight reads, scans, uniqueness checks, and write batches across browser tabs on runtimes with `navigator.locks`; Node tests and browsers without Web Locks fall back to a per-database in-process mutex shared by store instances in the same JS context.

Open lifecycle note: JS and WasmJS connections close on IndexedDB `versionchange`, blocked opens fail explicitly instead of hanging, and existing databases auto-upgrade to add missing object stores while preserving existing rows.

Request-wide native transaction note: wrapping the existing suspend request processor in one `IDBTransaction` is not viable. IndexedDB transactions are active only while requests are queued in the same browser task; the common processor suspends between reads, scans, validation, and write planning. A full native request transaction would require a different architecture: build a deterministic mutation plan first, then execute all required reads/checks/writes synchronously inside one JS/Wasm bridge transaction callback, or use browser-level locking plus compare-and-write validation.

4. Binary key helpers

Convert Kotlin `ByteArray` to JS `ArrayBuffer`/`Uint8Array` keys and back. Keep all Maryk ordering bytes unchanged.

5. Browser-only source sets

Likely structure:

- `commonMain`: datastore public API and common request routing.
- `commonMain`: datastore public API, request routing, codecs, and storage shape.
- `jsMain` and `wasmJsMain`: platform IndexedDB byte-store glue.

## Query Coverage

### Add/change/delete

Can be supported.

Need same writes as RocksDB:

- key marker.
- table values.
- index entries.
- unique entries.
- historic table/index/unique entries when `keepAllVersions`.
- update history rows when `keepUpdateHistoryIndex`.

Uniqueness checks work with direct `get` on unique object store inside the write transaction.

### Get

Can be supported.

Direct key reads from `keys` and `table`. Selected refs become bounded table cursor scans over `key + qualifier prefix`.

### Scan latest table

Can be supported well.

Use `IDBKeyRange.bound/lowerBound/upperBound` on `keys` object store. Use `openCursor(range, "next")` for ascending and `"prev"` for descending. Apply Maryk partial-key and filter checks while scanning.

### Scan latest index

Can be supported well.

Use `index` object store with key:

`indexReference + indexValue + primaryKey`

Range bounds map to `IDBKeyRange`. Cursor direction maps to Maryk order. Fetch table values by primary key for filters and selected values.

### Unique scan fast path

Can be supported well.

Direct `get` on `unique` object store. Historical unique scans `hu:<modelId>` for the latest visible entry <= `toVersion`, then reads the matching `ht:<modelId>` snapshot.

### Filters

Can be supported.

Optimization remains same as shared code:

- Equality/range filters on key or chosen index reduce cursor range.
- Other filters are post-filtered by fetching needed table values.
- Fuzzy qualifier and sub-reference filtering can be supported if the IndexedDB table cursor exposes real qualifier-prefix scans. Set capability flags to true only after passing `store:test`.

### ScanChanges/GetChanges

Implemented for the shared coverage in this branch.

`GetChangesRequest` now scans `c:<modelId>` by key prefix, filters by version bounds, applies `select`, and returns shared-test-compatible creation/change grouping.

`ScanChangesRequest` now handles table-order latest scans over IndexedDB key cursors and reads `c:<modelId>` rows per key. Covered shared cases include simple forward/reverse scans, limit, from/select, and non-history error behavior.

Index-ordered `ScanChanges` is implemented. Ascending index scans use bounded native index cursor ranges. Descending index scans cap the native cursor upper bound at the requested start sorting key, but still keep the lower side broad and filter/dedupe in query because the full Maryk sorting key includes `index value + primary key`, and naive descending lower bounds drop valid rows. Historic `toVersion` scans use `hi:<modelId>` and then read protobuf-encoded `c:<modelId>` rows per key/version. If a legacy or failed creation change-log row is missing, it reconstructs the creation changes from the durable value snapshot instead of falling back to memory.

### ScanUpdates/GetUpdates

Can be supported.

Implemented for current table-order scans over keys plus current values. With `keepUpdateHistoryIndex`, `ScanUpdateHistory` scans `uh:<modelId>` by inverted version order.

### ScanUpdateHistory

Implemented when `keepAllVersions = true` and `keepUpdateHistoryIndex = true`.

Use `updateHistory` cursor. Return failure when the index is disabled, matching existing tests.

### Historic scans with `toVersion`

Implemented for get/table/index/unique scans.

For table scans: read the latest `ht:<modelId>` snapshot per current key at `toVersion`.

For index scans: resolve visible `hi:<modelId>` state per `(indexValue, key)` at `toVersion`, then collapse the latest version per index entry before reading the `ht:<modelId>` snapshot.

This is the biggest performance risk for large historic index scans.

## Can It Handle All Query Types?

Yes, with conditions:

- Full correctness is feasible for current `store:test` coverage.
- Latest key/table/index/unique queries should be efficient.
- Simple `GetChanges` by key is implemented over IndexedDB scans.
- Current `GetUpdates`, table `ScanUpdates`, table/index `ScanChanges`, and `ScanUpdateHistory` are implemented over IndexedDB scans.
- Change validation blocks invalid values before they are persisted. Some exact exception-shape cases still differ from the shared JVM stores, especially change-layer errors that should surface before whole-object validation.
- Historic value queries are implemented through `ht:<modelId>` snapshots.
- Historic index queries are implemented through `hi:<modelId>` scans. They require temporary maps proportional to the scanned historic index result set.
- Historic unique queries are implemented through `hu:<modelId>` scans.
- Hard-delete purge for older `hi`/`hu` rows is implemented by scanning those IndexedDB stores for rows owned by the deleted key.
- Fuzzy qualifier and sub-reference filters are enabled and covered by shared datastore tests. Sub-reference filters resolve referenced rows through IndexedDB reads/scans, not by using an in-memory store.
- The `meta` object store records storage schema version, history options, and model signatures on open. That gives the browser store a migration/versioning anchor, but it is still lighter than FoundationDB's full metadata and lease machinery.
- Change logs are encoded with Maryk protobuf. Snapshot reconstruction remains as a defensive fallback for legacy or failed creation rows.
- Live flows remain in-memory listener state, as in all stores. Initial and refill data must come from IndexedDB, not the memory store.
- Browser quota, eviction, private browsing, and blocked upgrades are operational limits outside Maryk query semantics.

## What Not To Do

- Do not wrap `InMemoryDataStore` and persist snapshots.
- Do not load all records into maps for scans.
- Do not use string/base64 keys unless benchmarks prove no binary-key interop path works. String keys would risk ordering bugs and larger storage.
- Do not rely on IndexedDB secondary indexes for Maryk indexes. Maryk already encodes index keys exactly; native IDB indexes add little and make historic layouts awkward.

## Implementation Plan

1. Add `:store:indexeddb` to `settings.gradle.kts`.
2. Add module build file using JS convention. Disable or skip Node tests unless fake IndexedDB is added.
3. Add public `IndexedDbDataStore.open(name, dataModelsById, keepAllVersions, keepUpdateHistoryIndex)`.
4. Add IDB wrapper:
   - open/delete database.
   - schema upgrade creates object stores for all model/family combinations and auto-upgrades existing databases when a newly requested store is missing.
   - suspend request helpers.
   - cursor flow/loop helpers with range and direction.
5. Port RocksDB processor structure, replacing `DBAccessor`/`DBIterator` with async IDB access:
   - add/change/delete first.
   - get/getChanges.
   - table scan.
   - index and unique scan.
   - updates/history.
6. Run `store:test` against browser JS.
7. Run WasmJS browser tests.
8. Add docs to `store/README.md` once tests pass.

## Test Strategy

Current verification:

```bash
./gradlew :core:jvmTest :store:indexeddb:jsNodeTest :store:indexeddb:wasmJsNodeTest :store:indexeddb:jsBrowserTest :store:indexeddb:wasmJsBrowserTest
```

This passes on this branch.

Shared datastore cases currently reused:

- Add/get/delete basics.
- Current-state change basics, list/set/map/inc-map changes, complex deletes.
- Change validation for invalid scalar changes and list/set/map size constraints.
- `GetChanges` simple, `toVersion` rejection when history is disabled, `fromVersion`, `select`, and `maxVersions` rejection when history is disabled.
- `GetUpdates` simple current rows.
- `ScanChanges` simple forward/reverse, index order forward/reverse, limit, from/select, and non-history error behavior.
- `ScanUpdates` simple current rows.
- `ScanUpdateHistory` disabled-index failure and enabled version-ordered change/delete history.
- Table scans, index scans, unique checks, unique scan fast path.
- Historic `toVersion` get/table scans.
- Historic `toVersion` index scans, ascending and descending.
- Historic `toVersion` unique scans, prefix-collision guard, soft-delete inclusion, and soft-delete-by-change inclusion.
- Historic `toVersion` index-ordered scan changes.
- Historic hard-delete unique purge.
- IndexedDB byte-store ordering around `0x7f`, `0x80`, and `0xff`.
- Empty/exclusive IndexedDB range guards.
- Hard-delete purge after a unique value changed, covering both old and new historic unique rows.
- Existing database schema upgrade with preserved data and reopen after IndexedDB version bump.

Minimum:

- Reuse `runDataStoreTests` from `store:test`.
- Run against JS browser.
- Run against WasmJS browser.
- Add targeted tests for:
  - cursor startKey/includeStart behavior across more composite keys.
  - descending scans.
  - historic index scan with many versions for one key.
  - blocked open/close behavior.

Useful local Gradle shape after module exists:

```bash
./gradlew :store:indexeddb:jsBrowserTest
./gradlew :store:indexeddb:wasmJsBrowserTest
```

## Risks

- Kotlin/WasmJS IndexedDB externals may need hand-written JS interop. Keep wrapper small.
- IndexedDB transaction auto-close behavior can bite if coroutine suspension happens outside active request chaining. Keep all IDB requests issued through the active transaction and await request events carefully.
- Browser quota/eviction differs per browser.
- Multi-tab schema upgrades can be blocked by open connections.
- Performance of historic index scans may be materially worse than RocksDB for large histories.
- Test automation may need browser test infrastructure tuning; Node lacks native IndexedDB.

## Recommendation

Build it.

Start with latest-only support plus full table/index/unique scans over IndexedDB cursors. Then add `keepAllVersions` and update-history support. Gate capability flags and README claims on `store:test` passing in both JS browser and WasmJS browser.

## Current implementation snapshot

Done in this branch:

- Added `:store:indexeddb`.
- Added real IndexedDB byte-store wrappers for JS and WasmJS browser targets.
- Added fake-IndexedDB-backed Node test hooks without changing browser tests away from native IndexedDB.
- Switched the datastore layer away from a single-record blob experiment.
- Implemented a native current-state shape:
  - `k:<modelId>` => key -> `{firstVersion,lastVersion,isDeleted}`
  - `t:<modelId>` => `key-scope + qualifier -> encoded value`
  - `i:<modelId>` => `indexRef + encodedIndexValue + key -> key`
  - `u:<modelId>` => `uniqueRef + encodedValue -> key`
- Implemented native history shapes:
  - `c:<modelId>` => `key-scope + version -> versioned change`
  - `uh:<modelId>` => `inverted version + key -> versioned change`
  - `ht:<modelId>` => `key-scope + inverted version -> table snapshot`
  - `hi:<modelId>` => `indexRef + encodedIndexValue + key + inverted version -> active marker`
  - `hu:<modelId>` => `uniqueRef + encodedValue + inverted version -> key or tombstone`
- Implemented these requests directly over IndexedDB:
  - `AddRequest`
  - `ChangeRequest`
  - `GetRequest`
  - `DeleteRequest`
  - `GetChangesRequest`
  - `GetUpdatesRequest`
  - `UpdateResponse` processing for addition/change/removal/initial-changes updates
  - `ScanChangesRequest`
  - `ScanUpdatesRequest`
  - `ScanUpdateHistoryRequest`
  - `ScanRequest` for:
    - table scans
    - exact-key fast path
    - index scans
    - unique fast path
    - historic `toVersion` table/index/unique reads
- Reused shared datastore tests for:
  - add
  - change
  - get
  - table scan
  - index scan
  - unique validation
  - unique scan fast path
  - scan filters
  - reverse scans
  - aggregations
  - changes
  - updates
  - update history
  - historic reads/scans

Verified:

- `./gradlew :store:indexeddb:jsNodeTest`
- `./gradlew :store:indexeddb:wasmJsNodeTest`
- `./gradlew :store:indexeddb:jsBrowserTest`
- `./gradlew :store:indexeddb:wasmJsBrowserTest`

Production-readiness follow-up:

- Current key rows now carry the current value snapshot next to metadata. Exact 17-byte legacy metadata rows still decode and fall back to fine table rows.
- Table and current-index scans now use paged IndexedDB cursor batches. This preserves native cursor scans while making request limits count matched rows after soft-delete/filter skips.
- Fuzzy qualifier filtering and sub-reference filtering are enabled and covered by shared datastore tests. Referenced-object filters resolve referenced rows through IndexedDB reads/scans.
- The `meta` store records storage schema version, history options, model signatures, and encoded model definitions at open time. Startup migration now uses the stored definitions to classify safe adds, new-index backfills, and unsafe migrations.
- Sensitive field encryption is wired through `FieldEncryptionProvider`: table/historic payloads encrypt/decrypt, sensitive unique rows use deterministic tokens through `SensitiveIndexTokenProvider`, and sensitive indexed properties are rejected. JS and WasmJS now include browser-native WebCrypto AES-GCM/HMAC-SHA256 providers.
- JS value writes now use `Int8Array` values to reduce boxing. IndexedDB keys intentionally stay numeric array keys because that preserves Maryk bytewise ordering with IndexedDB array-key comparison.
- Paged scan logic and metadata writes have been split out of the main datastore file, but more processor-level decomposition would still improve long-term maintainability.

Real-browser demo validation:

- Ran `indexeddb-demo` at `http://localhost:8088/` against real browser IndexedDB.
- Seeded 10000 `Person` rows in 5679ms.
- Table scan read 10003 rows in 143ms.
- Ascending compound index scan read 10003 rows in 754ms.
- Descending compound index scan read 25 rows in 7ms.
- Equals filter on indexed surname returned 100 rows in 9ms.
- Prefix table scan over firstName returned 1000 rows in 124ms.
- Greater-than indexed scan returned 403 rows in 40ms.
- Start-key pagination read page2=100 rows in 3ms.
- Key-ordered `scanChanges` read 1000 rows in 240ms.
- Update-history scan read 1000 rows in 204ms.
- Reopen check passed; persisted row was still present.

Remaining production constraints:

- Request mutation scopes are cross-tab serialized only when Web Locks are available. Without Web Locks, they are serialized across store instances in the same JS context, but not across separate tabs/workers.
- A true request-wide native IndexedDB transaction still needs a queued synchronous IndexedDB operation plan, not a coroutine wrapper around the current common processor.
- Descending index-ordered `ScanChanges` still uses a broad lower index-prefix bound and filters/deduplicates in-query, though the upper side is bounded by the requested start sorting key.
- Change-log payloads are Maryk protobuf rows. Creation history can still be reconstructed from durable snapshots if a legacy or failed row contains the fallback marker.
- IndexedDB now has a local startup migration runtime. It handles safe adds, current/historic new-index backfill over real IndexedDB scans, and explicit migration hooks. It still does not implement RocksDB/FoundationDB-style leases, background continuation, persisted partial cursors, or cluster update logs.

WasmJS note:

- the branch now has a real WasmJS IndexedDB wrapper with JS bridge interop instead of the earlier stub
- `fake-indexeddb` is wired into `wasmJsTest`
- `./gradlew :store:indexeddb:wasmJsNodeTest` and `./gradlew :store:indexeddb:wasmJsBrowserTest` pass
- direct typed Wasm externals were fragile; the bridge keeps the platform-specific surface small

## Answer to the storage-shape question

Yes. A second storage shape is better.

Reason:

- full-record blobs are fine for point reads
- they are weak for qualifier scans, partial selects, and native Maryk filtering
- Maryk already wants ordered qualifier rows
- IndexedDB can scan those rows directly with bounded cursors

So the practical split should be:

- current-state row store for `Get` and latest `Scan`
- index/unique row stores for native indexed queries
- historic row stores for `toVersion`, changes, and update history

That is the shape this branch now starts using. It avoids a memory-store fallback for query execution and keeps scan behavior on real IndexedDB ranges.
