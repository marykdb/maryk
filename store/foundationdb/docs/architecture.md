# Maryk + FoundationDB: Architecture Overview

This document describes how the FoundationDB storage engine is structured inside Maryk, how requests flow through the system, and how the main components relate. It is intended for contributors who want to understand and maintain the implementation.

## Big Picture

Maryk defines a storage‑engine‑independent core. Each engine implements the same high‑level operations (Add/Change/Delete/Get/Scan/GetChanges/ScanChanges/Updates). The FoundationDB engine maps those operations onto FoundationDB transactions, directories (subspaces), and range reads.

At runtime:

- `FoundationDBDataStore` owns the FDB client (and optional tenant), opens the directory tree, and runs a single‑threaded coroutine actor that processes requests sequentially.
- “Processors” map each request type to concrete reads/writes against FDB.
- “Helpers” encapsulate encoding/decoding, qualifier matching, version math, and index/unique management.
- An in‑memory `Cache` helps de‑duplicate value decoding when a request needs the same property multiple times (e.g. aggregations).

Capabilities in the FDB engine:

- `keepAllVersions` is fully supported for data, uniques, and indexes. Latest and historic index scans are supported; historic scanning is used when a `toVersion` is provided.

## Core Components

### FoundationDBDataStore

- Entry point to the engine.
- Constructor flags:
  - `keepAllVersions`: whether to maintain historic data alongside latest.
  - `fdbClusterFilePath`, `tenantName`, `directoryRootPath`: FDB connectivity & subspace root.
  - `enableClusterUpdateLog`: optional cluster-wide live update propagation for `executeFlow` (writes updates into an FDB log and tails them back into the in-memory listener flow).
- Initializes per‑model directories via DirectoryLayer.
- Launches the store actor (coroutine) to process incoming requests one by one. Within the actor, every request is handled in an FDB transaction (or uses an iterator scoped to the transaction).

### Cluster Update Log (Optional)

Goal: cluster-wide `executeFlow` updates (multi-writer, multi-reader) using only FoundationDB.

Subspaces (under the configured store root + optional tenant):

- `__updates__/v1/log`: append-only log entries written in the same transaction as the table mutation.
  - Key uses a FoundationDB versionstamp to avoid collisions: `(shard, modelId, hlc_be_8, keyBytes, originIdBytes, versionstamp)`.
  - Value contains `(originId, modelId, type, version, keyBytes, payload)` where payload is values (Add), changes (Change), or hardDelete flag (Delete).
- `__updates__/v1/consumers/<consumerId>`: per-consumer cursors, one per shard. Each node/process must use a unique `consumerId`.
- `__updates__/v1/heads`: "wake keys" updated with versionstamped values. Tailers watch these to avoid polling the entire log when idle.
- `__updates__/v1/hlc`: per-node HLC markers. Each writer updates `hlc/<consumerId>` (8 bytes big-endian HLC timestamp) on every append.
- `__updates__/v1/hlc_max`: shard-local cluster HLC max markers. Each writer updates `hlc_max/<shard>` via atomic `BYTE_MAX` on every append.
  - A background HLC syncer on every node watches heads and refreshes `max(hlc_max/*, hlc/*)` so local write versions never lag behind cluster HLC, even with zero active `executeFlow` listeners.

Semantics:

- Delivery is at-least-once. On failure/restart (or on `consumerId` change), consumers can see duplicate updates within the retention window.
- No strict global order across shards. Ordering is stable within a shard (HLC + versionstamp).
- Retention is time-based. A background GC clears per-shard ranges older than a cutoff (retention + skew margin) using HLC order.

### TableDirectories

Encapsulates the FDB subspaces per model:

- Generic: `meta`
- Non‑historic: `keys`, `table`, `unique`, `index`.
- Historic: adds `table_versioned`, `unique_versioned`, `index_versioned`.

This provides simple, fast `pack()` prefixes for building FDB keys.

### Processors (per request)

- `processAddRequest` → validates input, checks uniques, writes `(version || value)` into `table`, updates indexes/uniques, and marks creation/latest versions.
- `processChangeRequest` → reads current values, validates, applies modifications, writes new `(version || value)` and updates indexes/uniques (including historic tombstones/snapshots if enabled).
- `processDeleteRequest` → soft delete writes a tombstone; hard delete clears keys, table, and optionally historic. Updates indexes/uniques accordingly.
- `processUpdateResponse` → applies externally supplied updates (Addition/Change/Removal/InitialChanges) into the store to synchronize state; InitialValues and OrderedKeys are rejected as they do not contain sufficient version/change context.
- `processGetRequest` → fetches values for keys, obeying `toVersion`, `select`, filters, and `filterSoftDeleted`.
- `processScanRequest` → scans by key or index (based on order), performs filtering, and returns values (or aggregates) for up to `limit` rows.
- `processGetChangesRequest`/`processScanChangesRequest` → same navigation as Get/Scan but reading “VersionedChanges” instead of full values, taking `fromVersion`/`toVersion`/`maxVersions` into account and returning the `sortingKey` (for index scans).

Each processor uses a combination of:

- A small transactional body (`tc.run { tr -> … }`) for reads/writes.
- Iterators (`getRange(...).iterator()`) for range scans on `keys/table/index`.
- The shared value codecs and qualifier matchers from core.

Note on index‑based Changes: the returned `sortingKey` is the `(indexValue || key)` byte slice (without version). Clients can use it for stable pagination and correlation across engines.

### Helpers

Some representative helpers used across processors:

- `packKey(prefix, vararg segments)` builds full FDB keys from subspace + dynamic parts.
- `setValue`, `setLatestVersion`, `setCreatedVersion` write latest and creation timestamps + values, and mirror into historic subspaces when enabled.
- `toReversedVersionBytes`, `readVersionBytes`, `readReversedVersionBytes` for version encoding/decoding.
- Qualifier matching utilities to evaluate Maryk filters against stored bytes efficiently (direct gets for exact references, small range scans for fuzzy references).
- Index/unique writers and historic value writers.
- Zero‑free qualifier encoding: `encodeZeroFreeUsing01`/`decodeZeroFreeUsing01` ensure qualifiers in historic keys contain no 0x00 bytes. A single 0x00 separator is placed between the encoded qualifier and the inverted version bytes for unambiguous split and ordering; forward scans then yield latest‑first entries efficiently.

## Request Flow (Example)

### Add
1. Actor receives `AddRequest` with N objects.
2. For each object:
   - Validate object; compute `(key, version)`.
   - Read unique keys to ensure no conflicts; throw validation if found.
   - Write `(version || value)` per property and mark creation/latest.
   - Write index/unique entries.
3. Emit updates on the shared flow (for listeners).

### Get
1. Actor receives `GetRequest(keys, select, where, toVersion, aggregations, …)`.
2. For each key inside a transaction:
   - Check existence (creation version) and run filter/soft‑delete checks.
   - Read either latest from `table` or historic from `table_versioned` up to `toVersion`.
   - Apply `select` graph to avoid unnecessary reads.
3. Aggregate fields if requested, using `Cache` to avoid re‑decoding the same value twice.

### Scan
1. Compute key ranges and/or index ranges (depending on `order`).
2. Scan `keys` (table scan) or `index` (index scan), filter rows, and collect up to `limit`.
3. Return `FetchByTableScan` or `FetchByIndexScan` metadata (direction, start/stop keys, etc.).

### GetChanges / ScanChanges
1. Navigate like Get/Scan, but read values as “versioned changes” rather than current snapshots.
2. For latest only, read from `table` and attach versions; for historic or `maxVersions > 1`, read from `table_versioned` and stop at `maxVersions` per field.
3. Return `sortingKey` for index scans (the `(indexValue || key)` bytes) so consumers can page or correlate properly.

## Versioning Model

- “Latest” and “Historic” are physically separate, so point reads are small and “as of” queries are clean.
- Versions use Maryk’s HLC (a 64‑bit timestamp) with inverted byte encoding in historic keys to make newest entries come first in range scans.
- Concurrency is handled by FDB transactions; we read‑for‑write inside the same transaction for uniques/validations.

## Filtering

Filters compose (And/Or/Not) around primitives (Exists, Equals, Range, Regex, ValueIn, Prefix…). The engine evaluates them by matching property references against stored qualifiers:

- Exact: direct value lookup (fast path).
- Fuzzy: small range scan under the property’s qualifier prefix.

Soft deletes are enforced as an independent check.

In the FDB engine, fuzzy and sub‑reference filtering are disabled by default; complex scans should prefer secondary indexes for performance and determinism.

## Indexes and Uniques

- Indexes: `indexRef + (indexValueBytes || keyBytes)` → `version` (latest) or `… + inv(version)` → tombstone/snapshot (historic).
- Uniques: `(uniqueRef || valueBytes)` → `(version || keyBytes)` (latest) and mirrored into historic if enabled.

For scans, we slice by `(indexValue || key)` to include/exclude `startKey` and page in either direction.

## Error Handling and Migrations

- Any `RequestException` or validation error is wrapped in structured statuses (e.g. `AlreadyExists`, `ValidationFail`).
- Schema migrations are coordinated through the `model` subspace and run during startup; they can schedule re‑indexing work where necessary.

## Why this design?

- FoundationDB excels at ordered keyspaces and range reads. Separating current vs historic and composing keys as `(context prefix || logical key || qualifier || version)` plays to these strengths.
- The engine mirrors the Maryk semantics already proven in other backends (RocksDB), which keeps the behavior consistent across storage engines and simplifies test reuse.

## Where to look in code

- Data store: `store/foundationdb/src/commonMain/kotlin/maryk/datastore/foundationdb/FoundationDBDataStore.kt`
- Processors: `store/foundationdb/src/commonMain/kotlin/maryk/datastore/foundationdb/processors/…`
- Helpers: `store/foundationdb/src/commonMain/kotlin/maryk/datastore/foundationdb/processors/helpers/…`
- Tests: `store/foundationdb/src/commonTest/kotlin/maryk/datastore/foundationdb/FoundationDBDataStoreTest.kt` and shared test suite in `store/test/…`
