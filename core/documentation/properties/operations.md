# Property Operations

Properties can be changed, deleted, checked and more. To do those you create
a property operation.

## Check property value
A `PropertyCheck` is used to check a property value so no operations are done
unless it is true. 

Check if firstName is `John`
```kotlin
Person.ref { firstName }.check("John")
```

## Change property value
A property value can be changes with a `PropertyChange`. It can also check
`valueToCompare` with current value before changing the property. Will only
succeed if property matches.

Change firstName to `Jane`
```kotlin
Person.ref { firstName }.change("Jane")
```

Only change firstName to `Jane` if current value is Janice
```kotlin
Person.ref { firstName }.change("Jane", valueToCompare = "Janice")
```

## Delete a property
A `PropertyDelete` can be used to delete a property. It can also check if
a current value matches `valueToCompare` before deleting.

Delete firstName
```kotlin
Person.ref { firstName }.delete()
```

Delete firstName if current value is Jane
```kotlin
Person.ref { firstName }.delete(valueToCompare = "Janice")
```

## Changes on Map, List and Set properties

[`Map`](types/map.md#operations), [`List`](types/list.md#operations) and 
[`Set`](types/set.md#operations) properties can also be changed. How that is done 
is described in their own documentation.
