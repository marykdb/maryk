# Property Operations

Properties can be changed, deleted, checked and more. To do those you create
a property operation.

## Check property value
A `Check` is used to check a property value so no operations are done
unless it is true. 

Check if firstName is `John`
```kotlin
Check(
    Person { firstName::ref } with "John",
    Person { lastName::ref } with "Smith"
)
```

## Change property value
A property value can be changed with a `Change`.

```kotlin
Change(
    Person { firstName::ref } with "Jane",
    Person { lastName::ref } with "Doe"
)
```

### Deletion
A property can be deleted by setting it to `null`.
```kotlin
Change(
    Person { firstName::ref } with null
)
```

## Changes on Map, List and Set properties

[`Map`](types/map.md#operations), [`List`](types/list.md#operations) and 
[`Set`](types/set.md#operations) properties can also be changed. How that is done 
is described in their own documentation.
