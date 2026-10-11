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

## Options

- `keepAllVersions`: Keep historic values/indexes/uniques for `toVersion`, changes, and history queries.
- `keepUpdateHistoryIndex`: Keep an engine-level update history index ordered by latest change. With this enabled, `scanUpdates(order = null)` uses newest-first change order by default.

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
