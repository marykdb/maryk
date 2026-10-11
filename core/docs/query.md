# Query Data

Maryk stores support a range of query actions. Requests can run locally or be serialized and sent to a remote store.

## Mental model

A Maryk query is a typed request object created from a `RootDataModel`.

```kotlin
val request = Person.get(key)
val response = store.execute(request)
```

The same request shape works against every store engine. That is why the CLI, App, Remote Store and embedded stores can share behavior.

Use:

- `Add`, `Change`, `Delete` for writes.
- `Get` when you know keys.
- `Scan` when you need a range, filter, index order or page.
- `GetChanges` / `ScanChanges` for object history.
- `GetUpdateHistory` / `ScanUpdateHistory` for bounded, newest-first historical update entries.
- `GetUpdates` / `ScanUpdates` for chronological sync.
- `executeFlow(...)` for live views.

Use reference graphs with reads to fetch only the fields you need.

## Common tasks

| Task | Request to start with |
| --- | --- |
| Insert a new object | `Model.add(...)` |
| Update known objects | `Model.change(key.change(...))` |
| Fetch by key | `Model.get(key)` |
| Browse a range or page | `Model.scan(...)` |
| Fetch only a few fields | Add `select` with a reference graph |
| Build a live view | `executeFlow(Model.scanUpdates(...))` |
| Inspect field history | `Model.getChanges(...)` or `Model.scanChanges(...)` |
| Page through record revisions | `Model.getUpdateHistory(key, ...)` |
| Browse historical update entries | `Model.scanUpdateHistory(...)` |

Start with `add`, `get`, and `scan`. Add filters, ordering, reference graphs, and update streams when the access pattern needs them.

## Basic Query Actions

Maryk exposes CRUD-style actions and query variants:

- Add, Change, Delete – mutate objects.
- Get, Scan – fetch current values.
- Get/Scan Updates – stream additions, changes, removals over time.
- Get/Scan Changes – fetch versioned changes per object from history.

### Add

With [`AddRequest`](../src/commonMain/kotlin/maryk/core/query/requests/AddRequest.kt), objects can be
added to a store. When applied, it will deliver an [`AddResponse`](../src/commonMain/kotlin/maryk/core/query/responses/AddResponse.kt)
with a status on each object added.

**Example:**

Kotlin:

```kotlin
val addRequest = Person.add(
    Person.create {
        firstName with "Jurriaan"
        lastName with "Mous"
    },
    Person.create {
        firstName with "John"
        lastName with "Smith"
    }
)
```

### Change

With [`ChangeRequest`](../src/commonMain/kotlin/maryk/core/query/requests/ChangeRequest.kt), objects can be
modified in a store. When applied, it will deliver an [`ChangeResponse`](../src/commonMain/kotlin/maryk/core/query/responses/ChangeResponse.kt)
with a status on each change.

Refer to [property operations](properties/operations.md) to see how to apply
changes to properties.

**Example:**

```kotlin
val person1Key // Key of person 1 to change
val person2Key // Key of person 2 to change

val changeRequest = Person.change(
    person1Key.change(
        Check(Person.ref { firstName } with "Jane"),
        Change(Person.ref { lastName } with "Doe")
    ),
    person2Key.change(
        Change(Person.ref { lastName } with "Smith")
    )
)
```

Simplify references with Kotlin `run{}`:

```kotlin
val person1Key // Key of person 1 to change
val person2Key // Key of person 2 to change

val changeRequest = Person.run {
    change(
        person1Key.change(
            Check(ref { firstName } with "Jane"),
            Change(ref { lastName } with "Doe")
        ),
        person2Key.change(
            Change(ref { lastName } with "Smith")
        )
    )
}
```

### Delete

With [`DeleteRequest`](../src/commonMain/kotlin/maryk/core/query/requests/DeleteRequest.kt),
objects can be deleted from a store. The objects can be deleted by passing
a list of object [`keys`](key.md). Objects can either be hard or soft deleted.
With a hard delete, the data is permanently removed; with a soft delete (the default), it remains in the store but is
not viewable unless specifically requested.
When applied, it will deliver an [`DeleteResponse`](../src/commonMain/kotlin/maryk/core/query/responses/DeleteResponse.kt)
with a status on each deletion.

**Example:**

```kotlin
val person1Key // Key of person 1 to change
val person2Key // Key of person 2 to change

val deleteRequest = Person.delete(
    person1Key,
    person2Key,
    hardDelete = true
)
```

### Atomic

Use `AtomicRequest` when several record mutations form one logical operation. The
existing Add, Change, Delete, and Collect & Inject APIs keep their existing
per-object or sequential behavior.

```kotlin
import maryk.core.query.requests.atomicRequest
import maryk.datastore.shared.executeAtomic

val request = atomicRequest(changeSetId = mutationId) {
    expectVersion(Dossier, dossierKey, dossierVersion)
    val observationKey = add(Observation, observationValues)
    add(Report, reportValues(observationKey))
}
val response = store.executeAtomic(request)
```

The builder performs no I/O. Additions allocate typed keys once, so another
addition can reference them. Retain the constructed request for retries. Direct
construction accepts existing single-record write requests:
`AtomicRequest(id, Model.add(values), OtherModel.change(change))`.

A supported store commits all members together, including values, deletion
markers, indexes, unique ownership, retained history, applicable durable update
logs, and the successful retry receipt. A validation or guard failure returns
`AtomicStatus.Rejected` and no member mutation commits. Its `responses` contains
only the failed member, identified by `failedRequestIndex` and `failedGuard`;
it never reports staged successes as persisted results. `Committed` contains
one response per mutation in request order. A store without the optional
capability returns `Unsupported`; it never falls back to ordinary writes.

`expectVersion` adds a read-only concurrency guard. `expect(model, key, Check(...))`
checks property values; an empty `expect` checks physical record existence,
including soft-deleted records. The direct request's `guards` accepts single-record,
check-only Change requests. All guards, embedded `Check` changes, and version
preconditions are evaluated against the initial state, before mutations. They
remain protected through commit. An unchanged guard record is not given a new
version or published as an update.

Each mutation addresses one record, and a record can appear in the mutation
list only once. At most 1,000 mutations and 1,000 guards are accepted. The encoded
request and successful response are each limited to 1 MiB; receipts add a
32-byte request digest. FoundationDB bounds affected transaction data to 4 MiB,
with a 10-second native timeout setting and a retry limit of 10; each native attempt remains
subject to FoundationDB's transaction lifetime and key/value limits. Receipts
use a manifest and chunks of at most 80,000 bytes in the same transaction.
The same receipt format is used for every request size.
RocksDB and IndexedDB bound staged read/write work to 4 MiB. IndexedDB merges
staged writes with pages of at most 256 stored rows, stopping at the requested
result boundary; a scan may fetch at most 100,000 rows. Memory deep-copies only
targeted records and affected index histories, with a shared budget of 100,000
copied nodes per change set. Untouched records and histories are shared, while
metadata lists are shallow-copied. These budgets never split a change set.

`atomicCapabilities` on an `IsDataStore` reports these fixed ceilings, or `null`
when atomic requests are unsupported. Implementations advertising atomic support
must provide capability metadata. Backend-specific fields are optional; absence
means that a corresponding ceiling is not advertised. A limit report is not a
reservation or a promise that every request below those limits can commit.

Budget rejections populate `AtomicResponse.failure` with `LimitExceeded`, the
stable budget name, `maximum`, and `actual`. Budget names are `mutations`,
`guards`, `requestBytes`, `responseBytes`, `workBytes`, `stagedNodes`, and
`scanRows`. FoundationDB also advertises native storage-cell key/value byte
ceilings and reports `transactionBytes`, `storageKeyBytes`, or `storageValueBytes`
for definitive native size failures. Their `actual` count can be absent when
the native API supplies only an error code. Reusing a committed ID with different contents reports
`ChangeSetMismatch`. Member validation failures retain their existing structured
response and index. `AtomicRequestLimitException` exposes count limits during
construction. Other storage failures may still report `OutcomeUnknown`; a
missing structured limit is not evidence of rollback.

Supported members are Add, Change of an existing record, and soft Delete or
restore. Nested atomic requests, reads/scans, Collect & Inject, hard deletes,
and creation through Change are rejected at construction. Existing model
validation applies. Unique claims are validated in mutation order; an exchange
that requires a temporary unique collision is rejected as a whole. Typed
references retain their existing validation semantics; this API does not add
implicit foreign-key constraints or cascading deletes.

A successful receipt is recorded in the same commit as the mutations. Repeating
an identical request with the same `changeSetId` returns the original committed
result without rerunning its now-stale guards. Reusing that ID with different
contents is rejected. Receipts contain a SHA-256 request digest, rather than a
copy of potentially sensitive input values, plus the successful operation
responses. Receipts are retained until the store is destroyed; Memory retains
them only for its open instance. `OutcomeUnknown` reports an unusable or ambiguous execution result. A timeout,
cancellation, or lost connection is not evidence of rollback. Retry the original request with the original ID to
establish its outcome; do not create a new ID for an uncertain retry.

Mutated records share a Maryk HLC version; no-op records retain their previous
version. The HLC is not a cross-writer commit-order token. Atomicity is scoped to
one datastore: one Memory instance, RocksDB database, FoundationDB store root,
or IndexedDB database. Durability follows the engine's existing configuration.
Independent reads may straddle a commit, and existing record-level update
streams and replication retain their existing delivery boundaries. Notifications
are published only after commit, but they are not a grouped cross-model stream.

### Get

With [`GetRequest`](../src/commonMain/kotlin/maryk/core/query/requests/GetRequest.kt),
multiple specific objects can be queried by their [key](key.md).
To select a subset of values in the query, use `select` with a [graph](reference-graphs.md).
It is possible to filter the results with [filters](filters.md) or include soft-deleted results by
passing `filterSoftDeleted=false`.

You can also view the objects at a certain version with `toVersion`
if the store supports viewing past versions.

When applied, it will deliver an [`ValuesResponse`](../src/commonMain/kotlin/maryk/core/query/responses/ValuesResponse.kt)
with a list of [`ValuesWithMetaData`](../src/commonMain/kotlin/maryk/core/query/ValuesWithMetaData.kt)
containing the `key`, `object`, `firstVersion`, `lastVersion`, and `isDeleted`.

**Example:**

```kotlin
val person1Key // Key of person 1 to change
val person2Key // Key of person 2 to change

val getRequest = Person.get(
    person1Key,
    person2Key
)
```

With all parameters:

```kotlin
val person1Key // Key of person 1 to change
val person2Key // Key of person 2 to change

val getRequest = Person.run {
    get(
        person1Key,
        person2Key,
        where = And(
            Equals(ref { firstName } with "Clark"),
            Exists(ref { lastName })
        ),
        toVersion = 2uL,
        filterSoftDeleted = false
    )
}
```

### Scan

With [`ScanRequest`](../src/commonMain/kotlin/maryk/core/query/requests/ScanRequest.kt),
multiple objects can be queried by passing a `startKey` to scan from and filters on key parts to end it.
To select a subset of values in the query, use `select` with a [graph](reference-graphs.md).
It is possible to filter the results with [filters](filters.md), order, or limit the results (default= 100).
You can also include soft-deleted results by passing `filterSoftDeleted=false`.

Additionally, you can view the objects at a certain version with `toVersion` if the store supports historical views.

When applied, it will deliver an [`ValuesResponse`](../src/commonMain/kotlin/maryk/core/query/responses/ValuesResponse.kt)
with a list of [`ValuesWithMetaData`](../src/commonMain/kotlin/maryk/core/query/ValuesWithMetaData.kt)
containing the `key`, `object`, `firstVersion`, `lastVersion`, and `isDeleted`.

For pagination, pass `response.nextCursor` unchanged into the next scan. The opaque,
versioned cursor stores both the primary key and the effective ordering boundary, so
index pagination does not need to re-read the boundary record. It is bound to the
model and query; changing the filter, order, selection, or historical version rejects
the cursor. `startKey` remains supported for direct boundaries and legacy clients,
but cannot be combined with `cursor`.
The cursor also carries a fingerprint of the root-key and secondary-index storage
layouts. A cursor created before a key or secondary-index layout change is rejected
even when the model name and query are unchanged; start a fresh scan after the
storage-layout migration. Changes to validation, defaults, or unindexed properties
remain compatible.
For multi-entry indexes, cursor paging emits each record once through its first
eligible entry in the requested direction.
Aggregations apply to the current page; combine page results in the application
when a total across every page is needed.

```kotlin
val firstPage = dataStore.execute(Logs.scan(order = Logs.ref { timeStamp }, limit = 50u))
val secondPage = firstPage.nextCursor?.let { cursor ->
    dataStore.execute(Logs.scan(order = Logs.ref { timeStamp }, limit = 50u, cursor = cursor))
}
```

```kotlin
val timedKey // Key starting at a certain time

val scanRequest = Logs.scan(
    startKey = timedKey
)
```

With all parameters:

```kotlin
val timedKey // Key starting at a certain time

val scanRequest = Logs.run {
    scan(
        startKey = timedKey,
        where = GreaterThanEquals(
            ref { severity } with Severity.ERROR
        ),
        order = ref { timeStamp }.descending(),
        filterSoftDeleted = false,
        limit = 50,
        toVersion = 2uL
    )
}
```

### Get/Scan Changes

Use [`GetChangesRequest`](../src/commonMain/kotlin/maryk/core/query/requests/GetChangesRequest.kt)
and [`ScanChangesRequest`](../src/commonMain/kotlin/maryk/core/query/requests/ScanChangesRequest.kt)
to fetch versioned changes as stored in the data store, grouped per object.

Response shape: a [`ChangesResponse`](../src/commonMain/kotlin/maryk/core/query/responses/ChangesResponse.kt)
containing a list of `DataObjectVersionedChange` entries with the object `key`, optional `sortingKey` (for index scans),
and a list of `VersionedChanges` items. Each `VersionedChanges` contains a `version` and a list of field-level changes
at that version (for example: `ObjectCreate`, `Change`, `ObjectDelete`).

Notes:
- Default `maxVersions` is 1; requesting more than 1 version or using `toVersion` requires the store to enable `keepAllVersions`.
- Aggregations are not supported for changes requests.
- For `ScanChanges`, filters cannot use mutable (non-final or non-required) properties. Ordering is allowed only on the
  primary key order or on required, final properties.

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

### Get/Scan Update History

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

### Get/Scan Updates

You can request updates on objects ordered by version with
[`GetUpdatesRequest`](../src/commonMain/kotlin/maryk/core/query/requests/GetUpdatesRequest.kt)
or [`ScanUpdatesRequest`](../src/commonMain/kotlin/maryk/core/query/requests/ScanUpdatesRequest.kt).
`maxVersions` (default=1) bounds the history read for each selected record. In
RocksDB and FoundationDB, it applies per stored property, so it is not a bound on
the total number of returned record revisions. The current Memory and IndexedDB
paths limit grouped non-creation revisions and handle creation separately.
Use update-history requests when you need a page bounded by historical entries.
Returning more than one version requires `keepAllVersions = true`.

When applied, it will deliver an [`UpdatesResponse`](../src/commonMain/kotlin/maryk/core/query/responses/UpdatesResponse.kt)
with a list of [`IsUpdatesResponse`](../src/commonMain/kotlin/maryk/core/query/responses/updates/IsUpdateResponse.kt)
which are either `AdditionUpdate`, `ChangeUpdate`, `RemovalUpdate`, and always starts with `OrderedKeysUpdate`,
reflecting the initial ordering of keys.

This request type can also be used inside `executeFlow` to listen to updates as they happen in the data store.

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
