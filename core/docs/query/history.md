# History and revision paging

Use the [query overview](../query.md) to choose a request and a write boundary.

## Get/Scan Changes

Use [`GetChangesRequest`](../../src/commonMain/kotlin/maryk/core/query/requests/GetChangesRequest.kt)
and [`ScanChangesRequest`](../../src/commonMain/kotlin/maryk/core/query/requests/ScanChangesRequest.kt)
to fetch versioned changes as stored in the data store, grouped per object.

Response shape: a [`ChangesResponse`](../../src/commonMain/kotlin/maryk/core/query/responses/ChangesResponse.kt)
containing a list of `DataObjectVersionedChange` entries with the object `key`, optional `sortingKey` (for index scans),
and a list of `VersionedChanges` items. Each `VersionedChanges` contains a `version` and a list of field-level changes
at that version (for example: `ObjectCreate`, `Change`, `ObjectDelete`).

Notes:
- Default `maxVersions` is 1; requesting more than 1 version or using `toVersion` requires the store to enable `keepAllVersions`.
- Aggregations are not supported for changes requests.
- For `ScanChanges`, filters cannot use mutable (non-final or non-required) properties. Ordering is allowed only on the
  primary key order or on required, final properties. These checks include enclosing
  properties and the underlying fields of named search indexes, including normalized
  or split fields. A final child of an optional or mutable parent is not immutable.

**Get changes with all parameters:**

```kotlin
val person1Key // Key representing person 1.
val person2Key // Key representing person 2.

val getRequest = Person.run {
    getChanges(
        person1Key,
        person2Key,
        select = graph { listOf(firstName, lastName) },
        where = And(
            Equals(ref { firstName } with "Clark"),
            Exists(ref { lastName })
        ),
        fromVersion = 1000uL,
        toVersion = 2000uL,
        maxVersions = 100u,
        filterSoftDeleted = false
    )
}
```

**Scan changes with all parameters:**

```kotlin
val timedKey // Key that indicates the starting point for scanning.

val scanRequest = Logs.scanChanges(
    startKey = timedKey,
    select = graph { listOf(timeStamp, severity, message) },
    where = GreaterThanEquals(Logs.ref { severity } with Severity.ERROR),
    order = ref { timeStamp }.descending(),
    limit = 50u,
    includeStart = true,
    fromVersion = 1000uL,
    toVersion = 2000uL,
    maxVersions = 100u,
    filterSoftDeleted = false
)
```

## Get/Scan Update History

Use `getUpdateHistory(key, ...)` for one known record and `scanUpdateHistory(...)`
for historical updates across a model. Both return an `UpdatesResponse` with
newest-first `AdditionUpdate`, `ChangeUpdate`, or `RemovalUpdate` entries. A record
may occur more than once. These are finite history queries, not live flows, and do
not include an `OrderedKeysUpdate` header.

`limit` counts matching `(key, version)` entries, rather than records or property
versions. All selected changes at one record version are returned together.
Both requests require `keepAllVersions` and a ready `keepUpdateHistoryIndex`.
The get request filters the key before materializing record data. Current engines
use the existing model-wide history index, so this does not promise a separate
record-key index lookup or constant scan work.

```kotlin
import maryk.core.query.requests.getUpdateHistory
import maryk.core.query.requests.scanUpdateHistory

val query = Person.getUpdateHistory(
    personKey,
    limit = 25u,
    filterSoftDeleted = false
)
val firstPage = store.execute(query)
val secondPage = firstPage.nextCursor?.let { cursor ->
    store.execute(query.copy(cursor = cursor))
}

val scanQuery = Person.scanUpdateHistory(limit = 25u)
val firstScanPage = store.execute(scanQuery)
val secondScanPage = firstScanPage.nextCursor?.let { cursor ->
    store.execute(scanQuery.copy(cursor = cursor))
}
```

`fromVersion` and `toVersion` are inclusive event-version bounds and must satisfy
`fromVersion <= toVersion` when an upper bound is supplied. Filters are
applied at each event's version; `select` limits the returned fields. Events with
no selected changes are omitted, while creation and deletion events remain
meaningful. Use `filterSoftDeleted = false` to include soft-delete transitions
and revisions whose record is soft-deleted at that version.

Forward `nextCursor` unchanged with the same request. A cursor binds the model,
record key for get requests, selection, filters, and version bounds. The page
size may change. Continuation is exclusive by version and then ascending record
key, so records sharing a version are neither skipped nor repeated. The cursor
also retains the first page's version ceiling, excluding later higher-version
writes. `nextCursor == null` means no more matching entries were found; lookahead
checks one additional matching entry before returning a cursor.

A cursor does not retain erased history or isolate later imports of older
versions. Hard deletion can remove earlier values permanently. Where a retained
hard-delete marker exists, an unfiltered query can return its `RemovalUpdate`;
filters requiring erased values cannot be evaluated in persistent stores.
Memory can evaluate hard-delete filters against its archived pre-delete values.

Creation entries contain selected values, and subsequent change entries contain
deltas. History paging does not return a complete snapshot for every entry; use
`get(key, toVersion = entry.version, ...)` for a retained point-in-time state.
