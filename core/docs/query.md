# Query Data

Maryk stores support a range of query actions. Requests can run locally or be serialized and sent to a remote store.

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
        Check(Person { firstName::ref } with "Jane"),
        Change(Person { lastName::ref } with "Doe")
    ),
    person2Key.change(
        Change(Person { lastName::ref } with "Smith")
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
multiple objects can be queried by passing a startKey to scan from and filters on key parts to end it.
To select a subset of values in the query, use `select` with a [graph](reference-graphs.md).
It is possible to filter the results with [filters](filters.md), order, or limit the results (default= 100).
You can also include soft-deleted results by passing `filterSoftDeleted=false`.

Additionally, you can view the objects at a certain version with `toVersion` if the store supports historical views.

When applied, it will deliver an [`ValuesResponse`](../src/commonMain/kotlin/maryk/core/query/responses/ValuesResponse.kt)
with a list of [`ValuesWithMetaData`](../src/commonMain/kotlin/maryk/core/query/ValuesWithMetaData.kt)
containing the `key`, `object`, `firstVersion`, `lastVersion`, and `isDeleted`.

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
    where = GreaterThanEquals(Logs { severity::ref } with Severity.ERROR),
    order = ref { timeStamp }.descending(),
    limit = 50u,
    includeStart = true,
    fromVersion = 1000uL,
    toVersion = 2000uL,
    maxVersions = 100u,
    filterSoftDeleted = false
)
```

### Get/Scan Updates

You can request updates on objects ordered by version with
[`GetUpdatesRequest`](../src/commonMain/kotlin/maryk/core/query/requests/GetUpdatesRequest.kt)
or [`ScanUpdatesRequest`](../src/commonMain/kotlin/maryk/core/query/requests/ScanUpdatesRequest.kt).
`maxVersions` (default=1) can be used to control how many versions are returned
at maximum. To return more than one version, the DataStore needs to have `keepAllVersions` set to `true`.

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
        order = ref { lastName }.ascending(),
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
        Logs { severity::ref } with Severity.ERROR
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
