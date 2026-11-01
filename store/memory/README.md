# Maryk Memory Store

In-memory Maryk data store implementation.
Fastest option for tests and local development. No persistence.

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

- `keepAllVersions`: Keep historic values/indexes/uniques for `toVersion`, changes, and history queries.
- `keepUpdateHistoryIndex`: Keep the update-history index used by newest-first historical pages. `ScanUpdates` may use this index to select records, but its emitted synchronization events remain chronological.

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
commit resolution. Query and mutation APIs shipped in 0.12.1 retain their existing
contracts outside an explicit transaction.


See [store behavior](../shared/docs/store-behavior.md) for staged Checks, typed
child mutations, unique ownership, replication and value ownership. For optional
SQL queries, follow the [SQL guide](../../sql/README.md).
