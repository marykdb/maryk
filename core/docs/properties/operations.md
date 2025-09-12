# Property Operations

Properties can be changed, deleted or checked with property operations.

## Check property value
A `Check` compares a property value; subsequent operations run only when the check passes.

Check if firstName is `John`
```kotlin
Check(
    Person { firstName::ref } with "John",
    Person { lastName::ref } with "Smith"
)
```

## Change property value
Use `Change` to update a property value.

```kotlin
Change(
    Person { firstName::ref } with "Jane",
    Person { lastName::ref } with "Doe"
)
```

### Deletion
Set a property to `null` to delete it.
```kotlin
Change(
    Person { firstName::ref } with null
)
```

## Changes on Map, List and Set properties

[`Map`](types/map.md#operations), [`List`](types/list.md#operations) and [`Set`](types/set.md#operations) properties have specialised change operations documented on their respective pages.
