# Maryk Stores

Maryk ships with multiple storage engines that implement the same datastore API. You can pick the engine that best matches your deployment and operational needs without changing your models or query code.

- Common logic lives in `store/shared` and powers every engine.
- All engines support Maryk’s versioned data model, indexes, uniques, and the standard request types (Add/Change/Delete/Get/Scan/GetChanges/ScanChanges/Updates).

## Selecting an Engine

- Need the fastest feedback loop or CI determinism? Use Memory.
- Want durable, embedded storage across desktop/mobile/server without running a service? Use RocksDB.
- Need ACID transactions and scale‑out on the server (JVM)? Use FoundationDB.

All engines share the same API surface, so you can start with Memory or RocksDB locally and move to FoundationDB on the server without changing your models or queries.

Below is a practical overview of each engine, why it was chosen, and when to use it.

### Memory

- Type: In‑memory, non‑persistent.
- Strengths: Extremely fast, zero setup, deterministic behavior; ideal for development and tests.
- Why it’s a great fit: Mirrors the same higher‑level logic as the persistent engines, so it’s perfect for unit and integration tests that exercise real request flows without I/O. Minimal dependencies and instant startup.
- Typical use cases: Unit tests, CI, prototyping, ephemeral caches, local experimentation.
- Learn more: `store/memory/README.md`.

### RocksDB

- Type: Embedded, persistent key‑value store with column families.
- Strengths: Ordered keyspace and efficient range scans; column family isolation; robust single‑node performance; broad platform support (JVM, iOS, macOS, Android, Linux).
- Why it’s a great fit: Maryk stores each model in multiple column families (keys/table/index/unique and historic variants when `keepAllVersions` is enabled). RocksDB’s ordered iteration and prefix/range reads map directly to Maryk’s layout, making latest and historic queries efficient without extra services.
- Typical use cases: Local‑first/embedded apps, desktop/mobile deployments, single‑node servers, moderate‑to‑large datasets without a separate database process.
- Learn more: `store/rocksdb/README.md` and `store/rocksdb/documentation/storage.md`.

### FoundationDB (JVM)

- Type: Distributed, transactional key‑value store accessed via the FoundationDB Java client.
- Strengths: Strict ACID transactions, ordered key space with fast range reads, subspaces/directories, horizontal scalability, and operational resilience.
- Why it’s a great fit: Maryk maps models to FDB directories and encodes versions so “latest” and “as‑of” reads are both efficient. Historic index scanning is supported when `keepAllVersions` is enabled, aligning with Maryk’s version‑aware queries. Strong consistency makes complex multi‑key updates safe.
- Typical use cases: Server‑side deployments needing strong consistency, online growth, and cluster operations; applications that benefit from time‑travel queries and transactional semantics.
- Learn more: `store/foundationdb/README.md` and `store/foundationdb/docs/*`.

## Shared Code

The `store/shared` module provides the common abstraction and orchestration used by every engine. This ensures identical behavior for requests, update streaming, and query semantics regardless of the underlying storage technology.

- Interface: `IsDataStore` defines the unified API (`execute`, `executeFlow` for live updates, `processUpdate` for syncing remote updates, `close`/`closeAllListeners`) plus capability flags such as `supportsFuzzyQualifierFiltering` and `supportsSubReferenceFiltering`.
- Base implementation: `AbstractDataStore` implements the coroutine actor and update flow wiring. It routes all requests through a single store channel, manages listener lifecycles, and exposes `executeFlow` to stream `Addition/Change/Removal` updates derived from writes.
- Update streaming: The `updates/*` utilities maintain per‑request listeners:
    - `UpdateListenerForGet` and `UpdateListenerForScan` compute and emit `IsUpdateResponse` events (addition/change/removal) in response to live changes.
    - `processUpdate` merges externally supplied `UpdateResponse` objects into the local store, enabling synchronization between engines or instances.
- Query optimization: `optimizeTableScan` converts eligible full‑table scans into index scans using equality filters, and enforces minimum key‑prefix requirements for efficient scans. This keeps scan behavior consistent across engines.
- Caching: `Cache` provides a small LRU cache per model/key to reuse decoded values across reads, reducing allocations and decode work for repeated property access.

Storage engines focus on the low‑level persistence (reads, writes, range iteration, encoding) while relying on this shared layer for request orchestration, update flows, and consistent semantics.

### Consistency and Tests

The `store/test` module contains a generic test suite (`runDataStoreTests`) that validates correctness and versioning across engines. Each engine reuses these tests to ensure uniform behavior for adds, changes, deletes, gets, scans, uniques, and update flows.
