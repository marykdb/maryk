# Synchronization and live views

Use the [query overview](../query.md) to choose a request and a write boundary.

## Get/Scan Updates

You can request updates on objects ordered by version with
[`GetUpdatesRequest`](../../src/commonMain/kotlin/maryk/core/query/requests/GetUpdatesRequest.kt)
or [`ScanUpdatesRequest`](../../src/commonMain/kotlin/maryk/core/query/requests/ScanUpdatesRequest.kt).
`maxVersions` (default=1) bounds the history read for each selected record. In
RocksDB and FoundationDB, it applies per stored property, so it is not a bound on
the total number of returned record revisions. The current Memory and IndexedDB
paths limit grouped non-creation revisions and handle creation separately.
Use update-history requests when you need a page bounded by historical entries.
Returning more than one version requires `keepAllVersions = true`.

When applied, it will deliver an [`UpdatesResponse`](../../src/commonMain/kotlin/maryk/core/query/responses/UpdatesResponse.kt)
with a list of [`IsUpdatesResponse`](../../src/commonMain/kotlin/maryk/core/query/responses/updates/IsUpdateResponse.kt)
which are either `AdditionUpdate`, `ChangeUpdate`, `RemovalUpdate`, and always starts with `OrderedKeysUpdate`,
reflecting the initial ordering of keys.

This request type can also be used inside `executeFlow` to listen to updates as they happen in the data store.

A live view may fetch a complete current row when a record enters its filter or
refills its window. If that read includes newer queued commits, the addition uses
the row's actual last version and already-included deltas are suppressed. This
keeps list operations and other non-idempotent changes from being applied twice.
If the removed key already matches again at a newer revision when the refill
reads it, the listener emits a removal followed by that complete addition,
preserving intervening edits. A refill at the change's exact revision retains
the usual delta response.
Ordering is derived from the same fetched values. Use finite history queries when
every retained revision is needed instead of a current live view.

When `FetchByUpdateHistoryIndex` supplies newest-first ordering, the initial
`OrderedKeysUpdate.sortingKeys` contains each corresponding key's complete source
version as eight unsigned big-endian bytes. These versions remain available when
`select` omits the latest changed property. Live history windows compare source
versions when adding or moving records, so an older queued event cannot displace
a newer row already fetched by a listener. This uses the existing optional
sorting field and does not change stored records.

Live scans retain their original cursor boundary for additions and refills,
including when deletion leaves the visible window empty. The public projection
also remains unchanged while internal reads obtain values needed for ordering.

When `ScanUpdates` uses the update-history index, rows are ordered by descending
revision, then descending record key for tied revisions, including live updates.
The separate finite `ScanUpdateHistory` cursor API retains its existing ascending
key order within a tied revision.

Optionally, you can pass an `orderedKeys` parameter to a `ScanUpdatesRequest`. This allows you to receive any changes to
that list as well. This way, you are assured to receive hard-deleted values or added values that had their values
changed,
ensuring they are within range of the passed order/limit.

**Get updates with all parameters:**

```kotlin
val person1Key // Key of person 1 to change
val person2Key // Key of person 2 to change

val getRequest = Person.run {
    getUpdates(
        person1Key,
        person2Key,
        select = graph {
            listOf(
                firstName,
                lastName
            )
        },
        where = And(
            Equals(ref { firstName } with "Clark"),
            Exists(ref { lastName })
        ),
        toVersion = 2000uL,
        filterSoftDeleted = false,
        fromVersion = 1000uL,
        maxVersions = 100
    )
}
```

**Scan updates with all parameters:**

```kotlin
val timedKey // Key starting at a certain time

val scanRequest = Logs.scanUpdates(
    startKey = timedKey,
    select = graph {
        listOf(
            timeStamp,
            severity,
            message
        )
    },
    where = GreaterThanEquals(
        Logs.ref { severity } with Severity.ERROR
    ),
    order = ref { timeStamp }.descending(),
    filterSoftDeleted = false,
    limit = 50,
    fromVersion = 1000uL,
    toVersion = 2000uL,
    maxVersions = 100,
    orderedKeys = listOf(log1Key, log2Key, log3Key)
)

```


#### Applying replicated changes

Replication's `InitialChangesUpdate` carries each record's complete revisions in
strictly increasing version order, with each version present once even when the
record appears in multiple groups. Every revision must be at or below the update
envelope's version. Stores reject violations of these version constraints before
applying any prefix or advancing their clock. Independently delivered stale
updates retain their existing no-op behavior.
