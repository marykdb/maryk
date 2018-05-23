# Property Operations

Properties can be changed, deleted, checked and more. To do those you create
a property operation.

## Check property value
A `PropertyCheck` is used to check a property value so no operations are done
unless it is true. 

Check if firstName is `John`
```kotlin
Check(
    Person.ref { firstName } with "John",
    Person.ref { lastName } with "Smith"
)
```

Maryk YAML:
```yaml
!Check
  firstName: John
  lastName: Smith
```

## Change property value
A property value can be changed with a `PropertyChange`.

```kotlin
Change(
    Person.ref { firstName } with "Jane",
    Person.ref { lastName } with "Doe"
)
```

Maryk YAML:
```yaml
!Change
  firstName: Jane
  lastName: Doe
```

## Delete a property
A `PropertyDelete` can be used to delete a property.

Delete firstName
```kotlin
Delete(
    Person.ref { firstName },
    Person.ref { lastName }
)
```

Maryk YAML:
```yaml
# single
!Delete firstName

# multiple
!Delete
- firstName
- lastName
```


## Changes on Map, List and Set properties

[`Map`](types/map.md#operations), [`List`](types/list.md#operations) and 
[`Set`](types/set.md#operations) properties can also be changed. How that is done 
is described in their own documentation.
