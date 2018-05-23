# Query Data

You can perform a selection of query actions on a store of data objects. This store can be local or the 
query action can be serialized and send to a store on another server to be performed there.

## Basic Query Actions

Maryk provides some basic query actions which are basically based on the CRUD model: you can add, change, get,
scan or delete objects. 

### Add

With [`AddRequest`](../common/src/main/kotlin/maryk/core/query/requests/AddRequest.kt) objects can be 
added to a store. When applied it will deliver an [`AddResponse`](../common/src/main/kotlin/maryk/core/query/responses/AddResponse.kt)
with a status on each object to add.

Example:
```kotlin
val addRequest = Person.add(
    Person(
        firstName = "Jurriaan",
        lastName = "mous"
    ),
    Person(
        firstName = "John",
        lastName = "Smith"
    )    
)
```

### Change

With [`ChangeRequest`](../common/src/main/kotlin/maryk/core/query/requests/ChangeRequest.kt) objects can be 
changed in a store. When applied it will deliver an [`ChangeResponse`](../common/src/main/kotlin/maryk/core/query/responses/ChangeResponse.kt)
with a status on each change.

Refer to [property operations](properties/operations.md) to see how to apply
changes to properties.

Example:
```kotlin
val person1Key // Containing the key of person 1 to change
val person2Key // Containing the key of person 2 to change

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
val person1Key // Containing the key of person 1 to change
val person2Key // Containing the key of person 2 to change

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
With [`DeleteRequest`](../common/src/main/kotlin/maryk/core/query/requests/DeleteRequest.kt)
objects can be deleted inside a store. The objects can be deleted by passing 
a list of object [`keys`](key.md). Objects can either be hard or soft deleted.
With a hard delete the data is certainly gone, and with soft delete (Default) it is
still in the store but not viewable unless specifically requested.
When applied it will deliver an [`DeleteResponse`](../common/src/main/kotlin/maryk/core/query/responses/DeleteResponse.kt)
with a status on each delete.

```kotlin
val person1Key // Containing the key of person 1 to change
val person2Key // Containing the key of person 2 to change

val changeRequest = Person.delete(
    person1Key,
    person2Key,
    hardDelete = true
)
```

### Get 
With [`GetRequest`](../common/src/main/kotlin/maryk/core/query/requests/GetRequest.kt)
multiple specific objects can be queried by their [key](key.md). It is possible to
filter the results with [filters](filters.md), order the results or to include
soft deleted results by passing `filterSoftDeleted=false`. 

It is also possible to view the objects at a certain version with `toVersion`
if the store supports viewing past versions.

When applied it will deliver an [`ObjectsResponse`](../common/src/main/kotlin/maryk/core/query/responses/ObjectsResponse.kt)
with a list with [`DataObjectWithMetaData`](../common/src/main/kotlin/maryk/core/query/DataObjectWithMetaData.kt)
containing the `key`, `object`, `firstVersion`, `lastVersion` and `isDeleted`.

```kotlin
val person1Key // Containing the key of person 1 to change
val person2Key // Containing the key of person 2 to change

val getRequest = Person.get(
    person1Key,
    person2Key
)
```

With all options
```kotlin
val person1Key // Containing the key of person 1 to change
val person2Key // Containing the key of person 2 to change

val getRequest = Person.run{
  get(
      person1Key,
      person2Key,
      filter = And(
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
With [`ScanRequest`](../common/src/main/kotlin/maryk/core/query/requests/ScanRequest.kt)
multiple objects can be queried by passing a startKey to scan from and filters on key parts to end it.
It is possible to filter the results with [filters](filters.md), order or limit the results (default= 100).
It is also possible to include soft deleted results by passing `filterSoftDeleted=false`.

It is also possible to view the objects at a certain version with `toVersion`
if the store supports viewing past versions.

When applied it will deliver an [`ObjectsResponse`](../common/src/main/kotlin/maryk/core/query/responses/ObjectsResponse.kt)
with a list with [`DataObjectWithMetaData`](../common/src/main/kotlin/maryk/core/query/DataObjectWithMetaData.kt)
containing the `key`, `object`, `firstVersion`, `lastVersion` and `isDeleted`.

```kotlin
val timedKey // Key which start at certain time

val scanRequest = Logs.scan(
    startKey = timedKey
)
```

With all options
```kotlin
val timedKey // Key which start at certain time

val scanRequest = Logs.run {
    scan(
        startKey = timedKey,
        limit = 50,
        filter = GreaterThanEquals(
            ref { severity } with Severity.ERROR
        ),
        order = ref { timeStamp }.descending(),
        toVersion = 2L,
        filterSoftDeleted = false
    )
}
```

### Scan/Get Changes
In stores which support it, it is possible to request all the changes with 
[`GetChangesRequest`](../common/src/main/kotlin/maryk/core/query/requests/GetChangesRequest.kt)
or [`ScanChangesRequest`](../common/src/main/kotlin/maryk/core/query/requests/ScanChangesRequest.kt)
by defining the `fromVersion` parameter. For the rest this call is the same
as [`GetRequest`](#get) / [`ScanRequest`](#scan)

When applied it will deliver an [`ObjectChangesResponse`](../common/src/main/kotlin/maryk/core/query/responses/ObjectChangesResponse.kt)
with a list with [`DataObjectChange`](../common/src/main/kotlin/maryk/core/query/changes/DataObjectChange.kt)
containing the `key`, the `lastVersion` and a list of `changes`.

Get with all options
```kotlin
val person1Key // Containing the key of person 1 to change
val person2Key // Containing the key of person 2 to change

val getRequest = Person.run {
    getChanges(
        person1Key,
        person2Key,
        filter = And(
            Equals(ref { firstName } with "Clark"),
            Exists(ref { lastName })
        ),
        order = ref { lastName }.ascending(),
        fromVersion = 1000L,
        toVersion = 2000L,
        filterSoftDeleted = false    
    )
}
```

Scan with all options
```kotlin
val timedKey // Key which start at certain time

val scanRequest = Logs.scanChanges(
    startKey = timedKey,
    limit = 50,
    filter = GreaterThanEquals(
        Logs.ref { severity } with Severity.ERROR
    ),
    order = ref { timeStamp }.descending(),
    fromVersion = 1000L,
    toVersion = 2000L,
    filterSoftDeleted = false    
)
```

### Scan/Get Versioned Changes
In stores which support it, it is possible to request all the changes ordered
by version with 
[`GetVersionedChangesRequest`](../common/src/main/kotlin/maryk/core/query/requests/GetVersionedChangesRequest.kt).
or [`ScanVersionedChangesRequest`](../common/src/main/kotlin/maryk/core/query/requests/ScanVersionedChangesRequest.kt).
`maxVersions` (default=1000) can be used to control how many versions are returned
at maximum.  
For the rest this call is the same as [`GetChangesRequest`/`ScanChangesRequest`](#scan/get-changes)

When applied it will deliver an [`ObjectVersionedChangesResponse`](../common/src/main/kotlin/maryk/core/query/responses/ObjectVersionedChangesResponse.kt)
with a list with [`DataObjectVersionedChange`](../common/src/main/kotlin/maryk/core/query/changes/DataObjectVersionedChange.kt)
containing the `key` and `changes` with a list of objects containing the version and changes.

Get versioned changes with all options
```kotlin
val person1Key // Containing the key of person 1 to change
val person2Key // Containing the key of person 2 to change

val getRequest = Person.run {
    getVersionedChanges(
        person1Key,
        person2Key,
        filter = And(
            Equals(ref { firstName } with "Clark"),
            Exists(ref { lastName })
        ),
        order = ref { lastName }.ascending(),
        fromVersion = 1000L,
        toVersion = 2000L,
        maxVersions = 100,
        filterSoftDeleted = false    
    )
}
```

Scan versioned changes with all options
```kotlin
val timedKey // Key which start at certain time

val scanRequest = Logs.scanVersionedChanges(
    startKey = timedKey,
    limit = 50,
    filter = GreaterThanEquals(
        Logs.ref { severity } with Severity.ERROR
    ),
    order = ref { timeStamp }.descending(),
    fromVersion = 1000L,
    toVersion = 2000L,
    maxVersions = 100,
    filterSoftDeleted = false    
)
```
