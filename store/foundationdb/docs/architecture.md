# Maryk + FoundationDB: Architecture Overview

This document describes how the FoundationDB storage engine is structured inside Maryk, how requests flow through the system, and how the main components relate. It is intended for contributors who want to understand and maintain the implementation.

## Big Picture

Maryk defines a storage‑engine‑independent core. Each engine implements the same high‑level operations (Add/Change/Delete/Get/Scan/GetChanges/ScanChanges/Updates). The FoundationDB engine maps those operations onto FoundationDB transactions, directories (subspaces), and range reads.

At runtime:

- `FoundationDBDataStore` owns the FDB client and directory tree. Its actor orders actions, dispatches bounded concurrent reads and serializes mutations.
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
  - `fdbClusterFilePath`, `directoryRootPath`: FDB connectivity & subspace root.
  - `clusterUpdateLogConfiguration`: optional cluster-wide live update propagation for `executeFlow` (writes updates into an FDB log and tails them back into the in-memory listener flow).
    Models with sensitive properties are rejected when this is enabled because cluster-log payload encryption is not implemented.
    Upgrading its legacy HLC-ordered log is coordinated: quiesce all readers and writers, upgrade every binary, then restart. Consumers drain persisted legacy entries before storing a one-way commit-ordered cursor. Mixed old/new binaries and rollback after activation are unsupported because old binaries cannot enforce or understand the transition.
- Initializes per‑model directories via DirectoryLayer.
- Launches the store actor and read workers. Ordinary add, change and delete processors use one FDB transaction per object; multi-object requests can therefore return partial results. Reads use transactions or iterators scoped to the required operation.

### Process lifecycle

`FoundationDBDataStore.close()` releases per-store resources while leaving the
process-wide FoundationDB client network available to other stores. A Native
host must close all stores, then call `FDB.instance().shutdown()` during process
termination. Shutdown stops the shared network worker before native runtime
teardown and is terminal: the network cannot restart in that process.

### Public atomic requests and transaction scopes

Explicit atomic requests and serializable transaction journals use one native
transaction per replay attempt across participating models, schema fences and
receipts. Preview aborts its native transaction; application suspension retains
no native snapshot. A datastore revision fence detects intervening Maryk writes,
including writes outside the models selected by the scope. A replay with an
expected revision reads this fence with a conflict dependency. Requests without
an expected revision read the token through `snapshot()` and rely on their
native record/range conflicts, avoiding unrelated retries without weakening
suspended-scope validation. Atomic requests keep
initial-state checks; interactive journals read earlier staged writes and support
repeated mutations and hard deletion. See the
[shared transaction contract](../../../core/docs/query.md#serializable-transactions).

### Cluster Update Log (Optional)

Goal: cluster-wide `executeFlow` updates (multi-writer, multi-reader) using only FoundationDB.

Subspaces (under the configured store root):

- `__updates__/v1/log`: append-only log entries written in the same transaction as the table mutation.
  - Current keys order by commit: `(shard, modelId, versionstamp, hlc_be_8, keyBytes, originIdBytes)`. Retained legacy keys use `(shard, modelId, hlc_be_8, keyBytes, originIdBytes, versionstamp)`.
  - Value contains `(originId, modelId, type, version, keyBytes, payload)` where payload is values (Add), changes (Change), or hardDelete flag (Delete).
  - Readers validate the complete six-field stored identity and require its model, event HLC, record key and origin to match the decoded payload in both formats. Malformed or mismatched entries are skipped while the physical cursor advances; stored rows remain untouched. Chunked payloads use the same check after reconstruction. Commit versions come from the validated versionstamp, independently of the event HLC.
- `__updates__/v1/consumers/<consumerId>`: per-consumer cursors, one per shard. Each node/process must use a unique `consumerId`.
- `__updates__/v1/heads`: "wake keys" updated with versionstamped values. Tailers watch these to avoid polling the entire log when idle.
- `__updates__/v1/hlc`: per-node HLC markers. Each writer updates `hlc/<consumerId>` (8 bytes big-endian HLC timestamp) on every append.
- `__updates__/v1/hlc_max`: shard-local cluster HLC max markers. Each writer updates `hlc_max/<shard>` via atomic `BYTE_MAX` on every append.
  - A background HLC syncer on every node watches heads and refreshes `max(hlc_max/*, hlc/*)` so local write versions never lag behind cluster HLC, even with zero active `executeFlow` listeners.

### Sensitive Field Encryption (Optional)

- Property wrappers can be marked sensitive in model definitions (`sensitive = true`).
- FoundationDB store encrypts sensitive value payloads before writing table/historic entries.
- Read paths auto-decrypt when encrypted payload marker is present.
- Current constraints:
  - only simple value properties are supported
  - sensitive+unique uses deterministic lookup tokens (provider must implement `SensitiveIndexTokenProvider`)
  - sensitive+indexed is not supported

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

Legacy stores can retain a current soft-delete marker without its matching
historic row. A current marker at or before the requested snapshot determines
the deletion state, even when an older historic marker exists. Later markers
cannot affect earlier snapshots. Historic values reconcile this state after
decoding, so projections and soft-delete filters agree. Change readers merge
only in-bound transitions and keep their soft-delete `maxVersions` limit;
finite history uses the exact event version and can recover a restoration even
when filtering deleted records.

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
3. Aggregate selected values and fetch any additional metric inputs within the
   same transaction. Repeated metric reads can reuse values within the record.

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

The FDB engine supports fuzzy qualifier and sub-reference filtering. Complex scans
should still prefer secondary indexes for predictable performance.

## Indexes and Uniques

- Indexes: `indexRef + (indexValueBytes || keyBytes)` → `version` (latest) or `… + inv(version)` → tombstone/snapshot (historic).
- Uniques: `(uniqueRef || valueBytes)` → `(version || keyBytes)` (latest) and mirrored into historic if enabled.

For scans, we slice by `(indexValue || key)` to include/exclude `startKey` and page in either direction.

## Error Handling and Migrations

Full migration operations guide: [Migrations](./migrations.md).

- Any `RequestException` or validation error is wrapped in structured statuses (e.g. `AlreadyExists`, `ValidationFail`).
- Schema migrations are coordinated through the `model` subspace.
- `migrationConfiguration.migrationHandler` uses `MigrationContext` and returns `MigrationOutcome` (`Success`, `Partial`, `Retry`, `Fatal`).
- `migrationConfiguration = MigrationConfiguration(...)` from `maryk.core.models.migration`.
- Optional phase hooks also live there: `migrationExpandHandler`, `migrationVerifyHandler`, `migrationContractHandler`.
- Long migrations can hand off from startup to background (`migrationConfiguration.migrationStartupBudgetMs`, `migrationConfiguration.continueMigrationsInBackground`).
- Pending models are request-blocked until migration completes (`assertModelReady` gate).
- Runtime operations:
  - `pendingMigrations`, `migrationStatus`, `migrationStatuses`, `awaitMigration`
  - `pauseMigration`, `resumeMigration`, `cancelMigration`
- Default lease is distributed (`FoundationDBMigrationLease`):
  - per-model lease record in metadata
  - owner token + TTL (`migrationLeaseConfiguration.migrationLeaseTimeoutMs`)
  - heartbeat renewal (`migrationLeaseConfiguration.migrationLeaseHeartbeatMs`)
  - takeover after TTL expiry when owner is gone

## Why this design?

- FoundationDB excels at ordered keyspaces and range reads. Separating current vs historic and composing keys as `(context prefix || logical key || qualifier || version)` plays to these strengths.
- The engine mirrors the Maryk semantics already proven in other backends (RocksDB), which keeps the behavior consistent across storage engines and simplifies test reuse.

## Where to look in code

- Data store: `store/foundationdb/src/commonMain/kotlin/maryk/datastore/foundationdb/FoundationDBDataStore.kt`
- Processors: `store/foundationdb/src/commonMain/kotlin/maryk/datastore/foundationdb/processors/…`
- Helpers: `store/foundationdb/src/commonMain/kotlin/maryk/datastore/foundationdb/processors/helpers/…`
- Tests: `store/foundationdb/src/commonTest/kotlin/maryk/datastore/foundationdb/FoundationDBDataStoreTest.kt` and shared test suite in `store/test/…`

### Complete-value checks

Both check-only and mixed Change requests compare complete values from the current
native transaction. Scalars use direct qualifier reads; each Check with complex
properties materializes the record once. Collection counts are not decoded as
collection values. A missing expected property fails validation. Checks after a
change see preceding staged writes, and a failed check aborts the record mutation.

### Native API read and write optimizations

Clock watermark buckets use atomic `BYTE_MAX` on canonical eight-byte unsigned
big-endian HLC values. This keeps the maximum across concurrent writers without
reading the bucket in each mutation. This internal metadata must retain its
canonical encoding; corrupt watermark metadata requires recovery rather than
using normal writes as a repair mechanism.

Table and index scans grow bounded candidate batches as needed. Gets and value
scans prefetch at most eight records, decode in request order and release native
reads on completion, failure or cancellation. Eligible scalar projections reuse
one selection layout and overlap value reads with creation and schema checks.
Unused speculative reads are canceled; decoded rows still pass the normal
existence, deletion and filter checks and the record/request byte budgets.
Current reads can reuse the prepared soft-delete marker. Full single-record
current Gets without aggregations use `WANT_ALL`; finite scans retain the
binding's default streaming mode. Streaming hints do not replace byte limits.
Current table scans also batch deletion and filter lookups before applying the
normal predicates. Version-history scans retain bounded, request-local creation
and deletion metadata caches under the pinned read version. Exact-version
deletion lookups use point reads; range reads start at the requested bound and
close native iterators on completion or early exit.

Ordinary Gets use one native transaction's snapshot. Scans and reusable contexts
pin a read version, while explicit contexts and flow boundaries capture it before
callbacks can suspend. Later native transactions reuse that version and check the
current schema fence. Schema readiness is checked within each transaction,
including when metadata reads overlap record preparation.

State backup uses the datastore's durable mutation revision instead of retaining
one native snapshot. Each page and allocation read checks that revision in a
fresh snapshot; schema fences and the revision are checked again before archive
publication. Quiescent exports can therefore span native MVCC windows. An
intervening Maryk mutation rejects the unfinished archive. See
[state backup limits](../../shared/docs/state-backups.md#capture-limits-that-affect-operation).

Request/maintenance policies and per-attempt tracing hooks are described in the
[configuration guide](configuration.md#transaction-policies-and-diagnostics).

### Retry markers and asynchronous ownership

Ordinary writes retain one receipt key and outcome across native retries. A
store allocates a durable owner ID on its first mutation; reads and no-ops do not
allocate one. Private markers live in `__meta__/ordinary_receipts`, separate from
public atomic receipts. The owner counter survives reopen and native backup,
and close clears only the current instance's namespace.

Successful invocation markers enter a bounded cleanup queue. Failed cleanup
batches are retried; a crash or queue overflow can leave orphan markers requiring
quiescent maintenance. Markers protect retries within one invocation. Cross-call
idempotence requires an application-supplied stable transaction ID.

Suspending database operations retain one native transaction through SDK
`onError` retries, preserving retry counts, timeouts and backoff. Each attempt
waits for its child work and checks cancellation before commit. Closing cancels
owned transactions and futures. Prepared reads release their native resources
and raw rows after decoding; synchronous codecs and filters retain a compatibility
bridge. Transaction attempts share one request read budget across their members,
with a fresh budget for each replay attempt.

Cluster-log readers compare idle-pass heads before arming committed watches and
also wake when local subscriptions change. These checks cover the gap between
scanning and watching without relying only on periodic polling.

Maintenance workers use disjoint whole-record partitions, independent scratch
namespaces and bounded native transactions under the same schema fence. Final
publication waits for all workers; cancellation leaves the rebuild fenced and
recoverable.

### Reproducing performance measurements

Run `:store:foundationdb:runFoundationDBPerformanceProbe` against the managed
isolated local cluster. It verifies returned rows/statuses and reports median
and p95 range timings, scan-versus-point ownership lookup, and bulk import with
one versus eight concurrent transactions. Timings are diagnostic output, not
unit-test assertions or guarantees for a remote production cluster.

Before publishing binding changes, use
`-PfoundationDbBindingsPath=/absolute/path/to/foundationdb-multiplatform` to compile
that checkout with Maryk's verified toolchain. This local source override is
explicit; normal builds resolve the pinned published binding artifact. Native
focused checks also accept `-PfoundationDbNativeTestFilter=package.Class.*`.

Read allocation and receipt cleanup comparisons are also available through
`:store:foundationdb:runFoundationDBPerformanceProbe --args=reads` and
`--args=receipts`. The receipt probe retains the previous set-copy algorithm as a
control. Both modes check their results, warm up, and report allocation counters
when the JVM exposes them. JVM and Native release measurements should be kept
separate from debug-test timings.

Compare throughput, latency and peak process memory together, using repeated
matched runs with named source revisions. Include cold reads and full-record
workloads as well as projections. Keep rejected variants and unfavorable results
with the benchmark evidence: fewer allocations, deeper prefetch or more
concurrency do not by themselves establish an improvement. In particular, a
streaming hint that helps a full-record read need not help finite scans.

### Optional JVM direct-buffer queries

The binding's JVM runtime extensions can reduce temporary heap allocation during
range reads. Configure them once at application startup, because the SDK setting
and buffer pool are process-wide:

```kotlin
import maryk.foundationdb.ApiVersion
import maryk.foundationdb.FDB
import maryk.foundationdb.jvm.enableDirectBufferQuery
import maryk.foundationdb.jvm.resizeDirectBufferPool

val fdb = FDB.selectAPIVersion(ApiVersion.LATEST)
fdb.resizeDirectBufferPool(poolSize = 8, bufferSize = 512 * 1024)
fdb.enableDirectBufferQuery(true)
```

This is opt-in: eight buffers reserve about 4 MiB of native memory, and the SDK
initializes its 64 MiB default pool before resizing. Larger/concurrent queries
need their own measurements and sizing. Native clients do not use this Java pool.
Maryk leaves the SDK default disabled; lower heap allocation does not by itself
imply lower total memory use or a useful latency improvement.
