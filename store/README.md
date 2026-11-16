# Maryk Stores

Maryk ships with multiple storage engines that implement the same datastore API. You can pick the engine that best matches your deployment and operational needs without changing your models or query code.

- Common logic lives in `store/shared` and powers every engine.
- All engines support Maryk’s versioned data model, indexes, uniques, and the standard request types (Add/Change/Delete/Get/Scan/GetChanges/ScanChanges/Updates).
- Memory, IndexedDB, RocksDB, and FoundationDB can optionally maintain an engine-level update history index via `keepUpdateHistoryIndex` to speed up newest-first history scans where supported.

## Selecting an Engine

- Need a store without external setup for tests or CI? Use Memory.
- Need durable browser-local storage for JS/WasmJS apps? Use IndexedDB.
- Want durable, embedded storage across desktop/mobile/server without running a service? Use RocksDB.
- Need distributed ACID storage and scale‑out (JVM or native with `libfdb_c` present)? Use FoundationDB.
- Need remote access to a local store over HTTP or SSH? Use Remote Store.

All engines share the same API surface, so you can start with Memory in tests, use IndexedDB in browser apps, use RocksDB for embedded persistence, and move to FoundationDB on the server without changing your models or queries.

For recovery and backend transfer, start with [Backup and restore](shared/docs/backup-restore.md).

For measured request costs, batching and projection tradeoffs, and deployment
considerations, see [Maryk 0.14 datastore performance](PERFORMANCE.md).

## Atomic changes across models

`executeAtomic(AtomicRequest)` is an explicit, optional capability for bounded
changes across root models in one store. It protects validation and concurrency
guards and commits the complete change-set or none of its member mutations.
Existing requests retain their current semantics. Inspect `supportsAtomicRequests`
before offering this operation; unsupported stores return an explicit response.
`atomicCapabilities` reports fixed operation and backend resource ceilings. Limit
rejections expose machine-readable details through `AtomicResponse.failure`.

| Engine | Atomic boundary |
| --- | --- |
| Memory | Private staging and publication in one store instance; volatile |
| RocksDB | One native transaction across participating column families; dependent point reads participate in conflict checking |
| FoundationDB | One retryable native transaction across participating model subspaces, including schema fences and update-log entries |
| IndexedDB | One final native batch under the database's Maryk writer lock or fenced lease; concurrent browser contexts share that boundary |
| Remote | One forwarded request to a supporting backend; every participating model and mutation is authorized first |

See [Atomic requests](../core/docs/query.md#atomic) for construction, limits,
retry receipts, supported operations, and read/update-stream boundaries.

## Engine guides

### Memory

Volatile storage for tests, CI and local experimentation, with no database
service to configure. See the [Memory guide](memory/README.md).

### RocksDB

Embedded persistent storage for desktop, mobile and single-node servers. See the
[RocksDB guide](rocksdb/README.md) for setup, tuning, history and recovery.

### IndexedDB

Persistent browser storage for JS and WasmJS, with native cursor scans, history,
migrations and encryption support. See the [IndexedDB guide](indexeddb/README.md)
for browser constraints and cross-context consistency.

### FoundationDB

Distributed transactional storage for server applications, using the
multiplatform FoundationDB client. See the [FoundationDB guide](foundationdb/README.md)
for client setup, cluster requirements and operations.

### Remote Store (HTTP/SSH)

Expose an existing store to applications and tooling over HTTP, optionally
through SSH. The App and programmatic client can connect remotely; the CLI
serves local stores but does not connect to remote endpoints. See the
[Remote guide](remote/README.md).

## Inspecting data

Use the [CLI](../cli/README.md) or [desktop App](../app/README.md) to browse
models, scan records and edit values while evaluating or debugging a store.

## Shared Code

[Store Shared](shared/README.md) provides the common request API, actor routing,
scan optimization and listener lifecycle. Engines implement persistence and
native commit boundaries. Decoded-value caches are request-local; see the
[cache and ownership rules](shared/docs/store-behavior.md#decoded-value-cache).

For synchronization, submit updates in source-version order and await each
call. Stores do not reorder concurrent deliveries or guarantee convergence for
independently retried updates. See [replication ordering](shared/docs/store-behavior.md#replication).

### Consistency and Tests

The [store test module](test/README.md) supplies shared conformance cases for requests,
history, transactions, uniqueness and update flows. Engine-specific tests cover
behavior that depends on native storage or platform constraints.

## Transactions

Atomic requests and transaction scopes share each built-in engine's commit
implementation. Use an atomic request for known writes with initial-state guards;
use a transaction for protected reads, repeated writes, hard deletion or
savepoints. See [Choosing a write boundary](../core/docs/query.md#choosing-a-write-boundary).

All built-in engines expose the [shared serializable transaction contract](../core/docs/query.md#serializable-transactions).
The scope combines queries and mutations across registered models, stages repeated
writes, supports savepoints and rollback, and publishes one native/COW commit.
Remote forwards the backend capability. Revision conflicts are conservative and
explicit; unsupported custom adapters report no transaction capability.
