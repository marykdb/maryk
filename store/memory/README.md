# Maryk Memory Store

In-memory Maryk data store implementation.
Useful for tests and local development. No persistence. Requests are processed serially
on the shared CPU dispatcher.

## Getting started

```kotlin
InMemoryDataStore.open(
    keepAllVersions = true,
    keepUpdateHistoryIndex = true,
    dataModelsById = mapOf(
        1u to Account,
        2u to Course
    )
).use {
    // Run operations on the store
    
    execute(
        Account.add(
            Account(
                username="test1",
                password="test1234"
            ),
            Account(
                username="test2",
                password="test1234"
            )
        )
    )
}
```

The `.use { ... }` scope closes the store automatically.

## When to use

- Unit/integration tests.
- Local development.
- CI with deterministic behavior and zero external dependencies.

## Limitations

- Data is lost on close/shutdown.
- Not suitable as persistent production storage.

Decoded cells retain the same precision as persistent storage: Time and
DateTime use their declared precision, GeoPoint uses seven decimal places,
Decimal uses its declared scale, and Float32 uses 32-bit precision on every
target. This also applies to collection members, typed values, allocation
results and packed value objects. Enum cells resolve their stored numeric ID.
Replacements with the same stored values remain no-ops.

## Options

| Option | Behavior |
| --- | --- |
| `keepAllVersions` | Keep historic values/indexes/uniques for `toVersion`, changes, and history queries. |
| `keepUpdateHistoryIndex` | Keep the update-history index used by newest-first historical pages. `ScanUpdates` may use this index to select records, but its emitted synchronization events remain chronological. |

`getUpdateHistory(key, ...)` and `scanUpdateHistory(...)` require both options
and return finite, newest-first pages using `UpdatesResponse.nextCursor`.
The limit counts record-version entries; change entries contain selected deltas.
Memory retains archived hard-deleted records and can evaluate deletion filters
against their pre-delete state. Persistent engines cannot evaluate such filters
after those values are erased. See [history paging](../../core/docs/query.md#getscan-update-history).


## Serializable transaction scopes

Memory replays into private copies and publishes the copies and receipt together. A fresh revision token also detects a restarted Memory backend. Receipts survive only for the lifetime of the store.

The [shared contract](../../core/docs/query.md#serializable-transactions) describes
read-your-own-writes, savepoints, conservative revision conflicts, deadlines and
commit resolution.


See [store behavior](../shared/docs/store-behavior.md) for staged Checks, typed
child mutations, unique ownership, replication and value ownership. For optional
SQL queries, follow the [SQL guide](../../sql/README.md).

## Portable backup and restore

Use `backupState` / `restoreState` for current application data without requiring
source history. Use `backup` / `restore` when retained revisions must survive;
that mode requires history on source and target. A state restore target must be
isolated, empty and history-disabled. See the [shared backup guide](../shared/docs/backup-restore.md)
for examples, consistency rules, backend support and native alternatives.

## Projection reads

Narrow scalar projections can skip unselected collection data in current and
historical Gets and Scans, including creation snapshots in change and update
scans. Eligible current multi-key Gets can also reuse decoded
repeated records. Each result retains independent mutable containers; filtering,
ordering and version metadata are preserved. See the shared
[cache and ownership rules](../shared/docs/store-behavior.md#decoded-value-cache).

## Historical index ordering

Historical index reads use version order even when replication delivers
different records' histories out of order. The latest historical claim wins;
equal versions retain applied order.
A release identifies its owner by key and creation version, so an earlier owner's
release cannot clear a replacement's historical claim. Current uniqueness checks
continue to use the successfully applied owner independently of historical order.

Point lookups over reordered histories scan directly for the first eight reads.
Further lookups prepare an ordered view; consumers that enumerate an ordered
history prepare it immediately. This adds sorting work and index metadata while
sharing record payloads. Already ordered histories do not need that extra view.
Preparation allocation also includes temporary work, so it is not the retained
view size.
