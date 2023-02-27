# Property Operations

Properties can be changed, deleted, checked and more. To do those you create
a property operation.

## Check property value
A `PropertyCheck` is used to check a property value so no operations are done
unless it is true. 

Check if firstName is `John`
```kotlin
Check(
    Person { firstName::ref } with "John",
    Person { lastName::ref } with "Smith"
)
```

## Change property value
A property value can be changed with a `PropertyChange`.

```kotlin
Change(
    Person { firstName::ref } with "Jane",
    Person { lastName::ref } with "Doe"
)
```

## Delete a property
A `PropertyDelete` can be used to delete a property.

Delete firstName
```kotlin
Delete(
    Person { firstName::ref },
    Person { lastName::ref }
)
```


## Changes on Map, List and Set properties

[`Map`](types/map.md#operations), [`List`](types/list.md#operations) and 
[`Set`](types/set.md#operations) properties can also be changed. How that is done 
is described in their own documentation.
