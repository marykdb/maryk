# Reading and writing records

Use the [query overview](../query.md) to choose a request and a write boundary.

## Add

With [`AddRequest`](../../src/commonMain/kotlin/maryk/core/query/requests/AddRequest.kt), objects can be
added to a store. When applied, it will deliver an [`AddResponse`](../../src/commonMain/kotlin/maryk/core/query/responses/AddResponse.kt)
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

## Change

With [`ChangeRequest`](../../src/commonMain/kotlin/maryk/core/query/requests/ChangeRequest.kt), objects can be
modified in a store. When applied, it will deliver an [`ChangeResponse`](../../src/commonMain/kotlin/maryk/core/query/responses/ChangeResponse.kt)
with a status on each change.

Refer to [property operations](../properties/operations.md) to see how to apply
changes to properties.

`Values.toChanges()` preserves empty embedded values as well as populated ones.
For creation replay, `RootDataModel.fromChanges(context, changes)` reconstructs
missing embedded and map parents from the explicit references, in operation
order, without inserting defaults or allocating incrementing-map keys. Ordinary
`Values.change` and local mutation requests still require their parent values
to exist before a child can be changed.

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

A whole list, set or map can be replaced with `Change(ref with replacement)`.
An empty replacement keeps the property present; a nullable `ref with null` removes
it when allowed by the definition. `Check(ref with expectedCollection)` compares
the complete collection, including an empty one. Checks see changes preceding them
in the same ordered change request; a failing check rolls back that record's changes.
These values also survive atomic request receipts, transaction journals and Remote
transport. Use collection operations when changing individual members.

## Delete

With [`DeleteRequest`](../../src/commonMain/kotlin/maryk/core/query/requests/DeleteRequest.kt),
objects can be deleted from a store. The objects can be deleted by passing
a list of object [`keys`](../key.md). Objects can either be hard or soft deleted.
With a hard delete, the data is permanently removed; with a soft delete (the default), it remains in the store but is
not viewable unless specifically requested.
When applied, it will deliver an [`DeleteResponse`](../../src/commonMain/kotlin/maryk/core/query/responses/DeleteResponse.kt)
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

## Get

With [`GetRequest`](../../src/commonMain/kotlin/maryk/core/query/requests/GetRequest.kt),
multiple specific objects can be queried by their [key](../key.md).
To select a subset of values in the query, use `select` with a [graph](../reference-graphs.md).
It is possible to filter the results with [filters](../filters.md) or include soft-deleted results by
passing `filterSoftDeleted=false`.

Results retain request order, including repeated keys. A live get subscription
updates every occurrence independently. Removing a key removes all its occurrences;
if it later matches again, its entries return to their original requested positions.

You can also view the objects at a certain version with `toVersion`
if the store supports viewing past versions.

When applied, it will deliver an [`ValuesResponse`](../../src/commonMain/kotlin/maryk/core/query/responses/ValuesResponse.kt)
with a list of [`ValuesWithMetaData`](../../src/commonMain/kotlin/maryk/core/query/ValuesWithMetaData.kt)
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

## Scan

With [`ScanRequest`](../../src/commonMain/kotlin/maryk/core/query/requests/ScanRequest.kt),
multiple objects can be queried by passing a `startKey` to scan from and filters on key parts to end it.
To select a subset of values in the query, use `select` with a [graph](../reference-graphs.md).
It is possible to filter the results with [filters](../filters.md), order, or limit the results (default= 100).
You can also include soft-deleted results by passing `filterSoftDeleted=false`.

Deleted-inclusive scans use primary-key table order, since deleted records no
longer own current unique or secondary-index entries and several deleted records
can retain the same unique value. Set `allowTableScan=true` when the primary-key
filter does not meet the model's minimum scan range. Explicit secondary-index
ordering with `filterSoftDeleted=false` is rejected rather than returning an
incomplete page. Normal scans that filter deleted records retain indexed ordering.

Additionally, you can view the objects at a certain version with `toVersion` if the store supports historical views.

When applied, it will deliver an [`ValuesResponse`](../../src/commonMain/kotlin/maryk/core/query/responses/ValuesResponse.kt)
with a list of [`ValuesWithMetaData`](../../src/commonMain/kotlin/maryk/core/query/ValuesWithMetaData.kt)
containing the `key`, `object`, `firstVersion`, `lastVersion`, and `isDeleted`.

An inclusive range bound on a composite-index part includes matching records
with later index parts. Later predicates are checked on their actual parts rather
than appended to the range boundary. This also preserves strict string comparisons:
`GreaterThan("Jan")` includes `"Janne"`, while `LessThanEquals("Jan")` excludes it.
These rules apply in either direction and to retained historical index reads.

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
Live scans retain this rule as records change: a row whose first eligible entry
falls before the cursor stays excluded even if it has another entry after it.
The cursor record itself remains excluded if its index values later move.
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
