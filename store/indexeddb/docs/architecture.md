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
- browsers without Web Locks use a renewable per-database lease stored atomically in IndexedDB;
- lease release notifications use `BroadcastChannel`, with timed retries as a compatibility fallback;
- fallback write batches verify the lease in the same native transaction, so an expired owner cannot commit;
- final writes are committed with one native IndexedDB `readwrite` batch transaction.

This gives good browser behavior without pretending IndexedDB transactions are long-lived coroutine scopes.

Atomic requests reuse the writer lock or fenced lease for the complete planning
phase. A coroutine-local byte-store overlay provides read-your-own-writes without
exposing staged data to background readers. Record writes, index/history changes,
commit-journal rows, the clock, and the retry receipt are sent to one native
`writeBatch`. Failed validation discards the overlay and its notifications.
The browser adapters explicitly implement `AtomicIndexedDbByteStore`; the generic
byte-store interface's sequential batch fallback does not establish this guarantee.

## Serializable transaction scopes

Interactive scopes replay an ordered read/write journal into the same private
byte-store overlay. Each preview holds the Maryk writer lock or fenced lease only
for that replay; application suspension retains no native IndexedDB transaction.
A datastore revision fence rejects changes by other Maryk writers between steps.
Commit publishes the final bytes, revision and retry receipt in one native batch.
Atomic requests use this engine with initial-state checks and a restricted write
set. See the [shared transaction contract](../../../core/docs/query.md#serializable-transactions).

## Versioning

`open` creates or upgrades all required object stores for the configured models and options. Existing connections close on `versionchange`, and blocked opens fail clearly instead of hanging.

The `meta` object store records:

- storage schema version;
- history options;
- model signatures;
- encoded model definitions;
- versioned per-model migration state. A `Running` marker is stored before each
  handler call; its phase, attempt, cursor, and `Partial`/`Retry`/fatal outcome
  diagnostics survive reopen. Successful completion removes the state.

## Relationship To Other Stores

IndexedDB follows the same Maryk request semantics as RocksDB/FoundationDB where browser constraints allow it:

- current table/index/unique scans are native cursor scans;
- historic reads use stored snapshots and historic secondary rows;
- update-history scans use an inverted version index when enabled;
- live flow listener state remains in memory, as in the other stores, while initial/refill reads come from IndexedDB.

Projected update scans evaluate filters against the full current or requested
historic record before applying the returned selection. This rule applies to
primary-key, secondary-index and newest-first history scans, so selecting fewer
fields does not change which records match.
