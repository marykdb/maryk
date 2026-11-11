# RocksDB history and transactions

Choose history retention when opening a store. The flags control which physical
column families Maryk maintains; see the [storage layout](storage.md).

## History retention

- `keepAllVersions` defaults to `true`, retaining historic table, index and unique
  values for time-travel and change-history reads.
- `keepUpdateHistoryIndex` defaults to `false`. Enable it to maintain the
  model-wide index keyed by change version and record key for historical paging.
  `ScanUpdates` may select records through this index; its synchronization events
  remain chronological.

For example, keep both history structures for a model named `Account`:

```kotlin
import maryk.datastore.rocksdb.RocksDBDataStore

val store = RocksDBDataStore.open(
    relativePath = "accounts.db",
    dataModelsById = mapOf(1u to Account),
    keepAllVersions = true,
    keepUpdateHistoryIndex = true,
)
```

Close the store after use. See the [module overview](../README.md) for a complete
opening and cleanup example.

## Revision paging

For finite, newest-first revision pages, use `getUpdateHistory(key, ...)` or
`scanUpdateHistory(...)` with both history options enabled and a ready history
index. Continue with `UpdatesResponse.nextCursor`; the limit counts complete
record-version entries, independently of property-history `maxVersions`.
Both queries scan the existing model-wide history index. See
[history paging](../../../core/docs/query/history.md#getscan-update-history) for filters,
projections, and hard-delete retention limits.

## Hard deletion

A hard delete permanently removes a record's current and historic values, including when `keepAllVersions` is enabled. An earlier `toVersion` query cannot recover erased values. Use soft deletion when historical recovery is required.

See [store behavior](../../shared/docs/store-behavior.md) for undelete, unique
ownership, replication ordering and scans that include deleted records.

## Snapshots and reopen

Committed atomic requests and transactions persist their version watermark with
their data and receipt, so snapshots remain current after reopening. See
[opening existing stores](migrations.md#opening-existing-stores) for the one-time
watermark repair on older stores.

## Serializable transaction scopes

RocksDB replays through a native optimistic transaction. Point reads and iterators include its pending writes; a revision key participates in conflict detection and changes in each normal write commit. Preview closes without commit, and durable receipts survive reopen.

The [shared contract](../../../core/docs/query/transactions.md) describes
read-your-own-writes, savepoints, conservative revision conflicts, deadlines and
commit resolution. Query and mutation APIs shipped in 0.12.1 retain their existing
contracts outside an explicit transaction.
