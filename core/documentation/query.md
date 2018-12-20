# Query Data

You can perform a selection of query actions on a store of data objects. This store can be local or the 
query action can be serialized and send to a store on another server to be performed there.

## Basic Query Actions

Maryk provides some basic query actions which are basically based on the CRUD model: you can add, change, get,
scan or delete objects. 

### Add

With [`AddRequest`](../src/commonMain/kotlin/maryk/core/query/requests/AddRequest.kt) objects can be 
added to a store. When applied it will deliver an [`AddResponse`](../src/commonMain/kotlin/maryk/core/query/responses/AddResponse.kt)
with a status on each object to add.

Example:

Maryk YAML:
```yaml
!Add
  dataModel: Person
  objectsToAdd:
  - firstName: Jurriaan
    lastName: Mous
  - firstName: John
    lastName: Smith
```
Kotlin:
```kotlin
val addRequest = Person.add(
    Person(
        firstName = "Jurriaan",
        lastName = "Mous"
    ),
    Person(
        firstName = "John",
        lastName = "Smith"
    )    
)
```

### Change

With [`ChangeRequest`](../src/commonMain/kotlin/maryk/core/query/requests/ChangeRequest.kt) objects can be 
changed in a store. When applied it will deliver an [`ChangeResponse`](../src/commonMain/kotlin/maryk/core/query/responses/ChangeResponse.kt)
with a status on each change.

Refer to [property operations](properties/operations.md) to see how to apply
changes to properties.

Example:
Maryk YAML:
```yaml
!Change
  dataModel: SimpleMarykObject
  objectChanges:
  - key: MYc6LBYcT38nWxoE1ahNxA
    changes:
      !Check
        firstName: Jane
      !Change
        lastName: Doe
  - key: lneV6ioyQL0vnbkLqwVw+A
    changes:
      !Change
        lastName: Smith
```
Kotlin:
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
With [`DeleteRequest`](../src/commonMain/kotlin/maryk/core/query/requests/DeleteRequest.kt)
objects can be deleted inside a store. The objects can be deleted by passing 
a list of object [`keys`](key.md). Objects can either be hard or soft deleted.
With a hard delete the data is certainly gone, and with soft delete (Default) it is
still in the store but not viewable unless specifically requested.
When applied it will deliver an [`DeleteResponse`](../src/commonMain/kotlin/maryk/core/query/responses/DeleteResponse.kt)
with a status on each delete.

Maryk YAML:
```yaml
!Delete
  dataModel: Person
  keys: 
  - WWurg6ysTsozoMei/SurOw
  - awfbjYrVQ+cdXblfQKV10A
  hardDelete: true
```
Kotlin:
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
With [`GetRequest`](../src/commonMain/kotlin/maryk/core/query/requests/GetRequest.kt)
multiple specific objects can be queried by their [key](key.md). 
To select a subset of values in the query use `select`.
It is possible to filter the results with [filters](filters.md), order the results or to include
soft deleted results by passing `filterSoftDeleted=false`. 

It is also possible to view the objects at a certain version with `toVersion`
if the store supports viewing past versions.

When applied it will deliver an [`ValuesResponse`](../src/commonMain/kotlin/maryk/core/query/responses/ValuesResponse.kt)
with a list with [`ValuesWithMetaData`](../src/commonMain/kotlin/maryk/core/query/ValuesWithMetaData.kt)
containing the `key`, `object`, `firstVersion`, `lastVersion` and `isDeleted`.

Maryk YAML:
```yaml
!Get
  dataModel: Person
  keys: 
  - WWurg6ysTsozoMei/SurOw
  - awfbjYrVQ+cdXblfQKV10A
```
Kotlin:
```kotlin
val person1Key // Containing the key of person 1 to change
val person2Key // Containing the key of person 2 to change

val getRequest = Person.get(
    person1Key,
    person2Key
)
```

With all options
Maryk YAML:
```yaml
!Get
  dataModel: Person
  keys: 
  - WWurg6ysTsozoMei/SurOw
  - awfbjYrVQ+cdXblfQKV10A
  select:
  - firstName
  - lastName
  filter: !And
  - !Equals
    firstName: Clark
  - !Exists lastName
  order: !Desc lastName
  toVersion: 2
  filterSoftDeleted: false
```
Kotlin:
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
With [`ScanRequest`](../src/commonMain/kotlin/maryk/core/query/requests/ScanRequest.kt)
multiple objects can be queried by passing a startKey to scan from and filters on key parts to end it.
To select a subset of values in the query use `select`
It is possible to filter the results with [filters](filters.md), order or limit the results (default= 100).
It is also possible to include soft deleted results by passing `filterSoftDeleted=false`.

It is also possible to view the objects at a certain version with `toVersion`
if the store supports viewing past versions.

When applied it will deliver an [`ValuesResponse`](../src/commonMain/kotlin/maryk/core/query/responses/ValuesResponse.kt)
with a list with [`ValuesWithMetaData`](../src/commonMain/kotlin/maryk/core/query/ValuesWithMetaData.kt)
containing the `key`, `object`, `firstVersion`, `lastVersion` and `isDeleted`.

Maryk YAML:
```yaml
!Scan
  dataModel: Logs
  startKey: Zk6m4QpZQegUg5s13JVYlQ
```
Kotlin:
```kotlin
val timedKey // Key which start at certain time

val scanRequest = Logs.scan(
    startKey = timedKey
)
```

With all options
Maryk YAML:
```yaml
!Scan
  dataModel: Logs
  startKey: Zk6m4QpZQegUg5s13JVYlQ
  select:
    - timeStamp
    - severity
    - message
  filter: !GreaterThanEquals
    severity: ERROR
  order: !Desc timeStamp
  filterSoftDeleted: false
  limit: 50
  toVersion: 2
```
Kotlin:
```kotlin
val timedKey // Key which start at certain time

val scanRequest = Logs.run {
    scan(
        startKey = timedKey,
        filter = GreaterThanEquals(
            ref { severity } with Severity.ERROR
        ),
        order = ref { timeStamp }.descending(),
        filterSoftDeleted = false,
        limit = 50,
        toVersion = 2L
    )
}
```

### Scan/Get Changes
In stores which support it, it is possible to request all the changes ordered by version with 
[`GetChangesRequest`](../src/commonMain/kotlin/maryk/core/query/requests/GetChangesRequest.kt).
or [`ScanChangesRequest`](../src/commonMain/kotlin/maryk/core/query/requests/ScanChangesRequest.kt).
`maxVersions` (default=1) can be used to control how many versions are returned
at maximum.  
For the rest this call is the same as [`GetChangesRequest`/`ScanChangesRequest`](#Scan/Get Changes)

When applied it will deliver an [`ChangesResponse`](../src/commonMain/kotlin/maryk/core/query/responses/ChangesResponse.kt)
with a list with [`DataObjectVersionedChange`](../src/commonMain/kotlin/maryk/core/query/changes/DataObjectVersionedChange.kt)
containing the `key` and `changes` with a list of objects containing the version and changes.

Get versioned changes with all options
Maryk YAML:
```yaml
!GetChanges
  dataModel: Person
  keys: 
  - WWurg6ysTsozoMei/SurOw
  - awfbjYrVQ+cdXblfQKV10A
  filter: !And
  - !Equals
    firstName: Clark
  - !Exists lastName
  order: !Desc lastName
  toVersion: 2000
  filterSoftDeleted: false
  fromVersion: 1000
  maxVersions: 100
```
Kotlin:
```kotlin
val person1Key // Containing the key of person 1 to change
val person2Key // Containing the key of person 2 to change

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
        filter = And(
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

Scan versioned changes with all options
Maryk YAML:
```yaml
!ScanChanges
  dataModel: Logs
  startKey: Zk6m4QpZQegUg5s13JVYlQ
  filter: !GreaterThanEquals
    severity: ERROR
  order: !Desc timeStamp
  filterSoftDeleted: false
  limit: 50
  fromVersion: 1000
  toVersion: 2000
  maxVersions: 100
```
Kotlin:
```kotlin
val timedKey // Key which start at certain time

val scanRequest = Logs.scanChanges(
    startKey = timedKey,
    select = graph {
        listOf(
            timeStamp,
            severity,
            message
        )
    },
    filter = GreaterThanEquals(
        Logs.ref { severity } with Severity.ERROR
    ),
    order = ref { timeStamp }.descending(),
    filterSoftDeleted = false,    
    limit = 50,
    fromVersion = 1000L,
    toVersion = 2000L,
    maxVersions = 100
)
```
