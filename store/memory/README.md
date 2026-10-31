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

Replicated records that are already soft deleted retain their values without
claiming unique indexes. Undeleting them validates and reinstalls every stored
unique value, including when no active record previously used that unique field.
Changes validate and reconcile unique claims from the complete staged record,
including descendants of replaced or deleted embedded values and collections.
Float32 and Float64 unique claims distinguish raw NaN bit patterns, matching
persistent stores. Reusing the same pattern conflicts; a different pattern can
belong to another record. Numeric index ordering and map/set qualifier NaN
normalization retain their existing semantics.
Successful replicated no-op changes advance the record's replay watermark, so
older events cannot overwrite the acknowledged state. Local no-op requests retain
their existing version and listener behavior.

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


SQL reporting, metadata, embedded scalar writes, transactional upsert, bounded
windows, joined mutations, correlated queries and complex map/collection expansion
use the common SQL engine over this store's public requests. See the [SQL guide](../../sql/README.md) for supported
syntax, execution strategies, transaction tooling and resource limits. The shared
SQL conformance contract verifies these features through this adapter.

Check statements read the current staged values. Complex properties are materialized
once per Check, including provisional history nodes from preceding changes in the
same request. Scalar checks keep direct qualifier lookup. A failed Check rejects the
record mutation; empty collections remain distinct from absent properties. See the
[shared query contract](../../core/docs/query.md#change).
Checks treat inactive typed children as absent even when old child cells remain.

Typed child mutations must match the parent's current staged case. Writes into
inactive cases reject the record mutation, and inactive child deletions have no
effect. Activate a new case through the multi-type parent before editing its
children. Direct simple-case aliases cannot encode a standalone stored child;
replace the multi-type parent instead. These guards keep history/export readable
and do not change stored bytes for valid mutations.

Stored simple typed cells own immutable wrappers. Committed addition and change
notifications copy built-in value containers and mutation operands, so reusing or
mutating caller-owned request values after execution cannot rewrite stored history
or later replication events.
