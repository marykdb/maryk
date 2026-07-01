# IndexedDB Store Architecture

The IndexedDB store maps Maryk's ordered key-value storage model to browser-native IndexedDB primitives.

## Layers

`IndexedDbDataStore`

- Implements the Maryk datastore request surface.
- Reuses shared request/update logic where possible.
- Encodes Maryk keys, qualifiers, values, history rows, index rows, and unique rows.
- Chooses table, index, unique, history, or update-history paths per request.

`IndexedDbByteStore`

- Small common byte-store interface.
- Provides `get`, `put`, `delete`, `scan`, and `writeBatch`.
- Platform implementations live in `jsMain` and `wasmJsMain`.
- Tests inject `fake-indexeddb`; browser apps use the real browser IndexedDB implementation.

`IndexedDbScanBatches`

- Pages cursor scans in bounded batches.
- Lets Maryk request limits count matched rows after soft-delete/filter skips.
- Avoids full native range materialization for table/current-index/historic-index paths.

## Native IndexedDB Use

The store uses:

- object stores per Maryk model/family;
- `IDBKeyRange` bounds for start/end keys;
- forward and reverse cursors;
- direct object-store `get` for key and unique fast paths;
- native `readwrite` transactions for write batches.

Keys are stored as numeric arrays because IndexedDB array-key ordering preserves Maryk bytewise ordering. JS values are stored as `Int8Array` to avoid boxed value arrays.

## Mutation Consistency

The common Maryk processor suspends between validation, reads, scans, and write planning. Browser IndexedDB transactions become inactive when request queueing pauses across tasks, so the whole logical request is not wrapped in one native transaction.

Instead:

- mutation requests run under an exclusive Web Locks API lock when available;
- browsers without Web Locks and Node tests use a per-database in-process mutex;
- final writes are committed with one native IndexedDB `readwrite` batch transaction.

This gives good browser behavior without pretending IndexedDB transactions are long-lived coroutine scopes.

## Versioning

`open` creates or upgrades all required object stores for the configured models and options. Existing connections close on `versionchange`, and blocked opens fail clearly instead of hanging.

The `meta` object store records:

- storage schema version;
- history options;
- model signatures;
- encoded model definitions;
- migration state used by startup checks.

## Relationship To Other Stores

IndexedDB follows the same Maryk request semantics as RocksDB/FoundationDB where browser constraints allow it:

- current table/index/unique scans are native cursor scans;
- historic reads use stored snapshots and historic secondary rows;
- update-history scans use an inverted version index when enabled;
- live flow listener state remains in memory, as in the other stores, while initial/refill reads come from IndexedDB.
