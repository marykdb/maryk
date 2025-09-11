# Query Data

You can perform a selection of query actions on a store of data objects. This store can be local, or the
query action can be serialized and sent to a store on another server to be performed there.

## Basic Query Actions

Maryk provides some basic query actions which are fundamentally based on the CRUD model: you can add, change, get,
scan, or delete objects.

The **Get** and **Scan** request types are available in three variations:

- `GetRequest`/`ScanRequest` - Returns `Values` to represent the object as it is at the requested time.
- `GetUpdatesRequest`/`ScanUpdatesRequest` - Returns `Updates` like `Addition`/`Change`/`Removal` to show
  the changing of data and its view defined by filters and limit/offset over time. It is possible to use this in
  `executeFlow`
  to get live updates as new data is processed.
- `GetChangesRequest`/`ScanChangesRequest` - Returns any data as versioned changes as they are stored in the datastore
  ordered per data object.

### Add

With [`AddRequest`](../src/commonMain/kotlin/maryk/core/query/requests/AddRequest.kt), objects can be
added to a store. When applied, it will deliver an [`AddResponse`](../src/commonMain/kotlin/maryk/core/query/responses/AddResponse.kt)
with a status on each object added.

**Example:**

Kotlin:

```kotlin
val addRequest = Person.add(
    Person.create {
        firstName += "Jurriaan"
        lastName += "Mous"
    },
    Person.create {
        firstName += "John"
        lastName += "Smith"
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
It is possible to filter the results with [filters](filters.md), order the results, or include
soft-deleted results by passing `filterSoftDeleted=false`.

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
        order = ref { lastName }.ascending(),
        toVersion = 2L,
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

Additionally, you can view the objects at a certain version with `toVersion`
if the store supports viewing past versions.

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
        toVersion = 2L
    )
}
```

### Scan/Get Updates

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
        toVersion = 2000L,
        filterSoftDeleted = false,
        fromVersion = 1000L,
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
    fromVersion = 1000L,
    toVersion = 2000L,
    maxVersions = 100,
    orderedKeys = listOf(log1Key, log2Key, log3Key)
)

val person1Key // Key representing person 1 whose data will be changed.
val person2Key // Key representing person 2 whose data will be changed.

val getRequest = Person.run {
    getChanges(
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
        toVersion = 2000L,
        filterSoftDeleted = false,
        fromVersion = 1000L,
        maxVersions = 100
    )
}

val timedKey // Key that indicates the starting point for scanning at a certain time.

val scanRequest = Logs.scanChanges(
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
    fromVersion = 1000L,
    toVersion = 2000L,
    maxVersions = 100
)
```
