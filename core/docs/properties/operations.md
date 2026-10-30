# Property Operations

Properties can be changed, deleted or checked with property operations.

## Check property value
A `Check` compares the complete property value at its position in the change
request, including changes preceding it. A failed check rolls back that record's
changes. Empty lists, sets, and maps differ from absent properties; list order
matters, while sets and maps compare their complete contents.

Check if firstName is `John`
```kotlin
Check(
    Person.ref { firstName } with "John",
    Person.ref { lastName } with "Smith"
)
```

## Change property value
Use `Change` to update a property value.

```kotlin
Change(
    Person.ref { firstName } with "Jane",
    Person.ref { lastName } with "Doe"
)
```

A whole list, set, or map can also be replaced with `Change(ref with replacement)`.
An empty replacement keeps the property present and must satisfy its size
constraints. Use the member operations below for incremental edits.

Changing an embedded value replaces that complete value, including when the
replacement is empty. Use a child reference to change selected fields. Ordered
operations and reference-value pairs observe earlier changes in the same request;
`Values.change` preserves its original values and caller-owned replacement
collections and inserted values. The separate `Values.copy` API retains its
embedded merge behavior.

In `Values.change`, a wildcard child operation applies the complete child path
independently to every matching map value or list item. A typed child reference
addresses the active case only: deleting an inactive child has no effect, while
setting it rejects the change. Replace the multi-type parent to select a new case.

### Deletion
Set a property to `null` to delete it.
```kotlin
Change(
    Person.ref { firstName } with null
)
```

## Changes on Map, List and Set properties

[`Map`](types/map.md#operations), [`List`](types/list.md#operations) and [`Set`](types/set.md#operations) properties have specialised change operations documented on their respective pages.

## Value ownership

Each `emptyValues()` result owns its mutable item collection. The
`copyWithOwnedValues()` extensions copy built-in `Values`, collection and typed
value operands when a value or change must outlive its caller's buffers. Copies
preserve mutable typed wrapper kinds where operation equality depends on them.
