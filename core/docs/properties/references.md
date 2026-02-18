# Property References

To filter on a property you need a property reference. For example, to find all people named `Smith` you need a reference to the `firstName` property. Other databases might call this a column. Any [`DataModel`](../datamodel.md) can supply these references.

These operations can be defined in Kotlin or any of the serialization formats Maryk supports. 

## Example models

This `Person` model has two top level fields (`firstName`, `lastName`) and an embedded `Address`:
```kotlin
object Person : RootDataModel<Person>() {
    val firstName = string(index = 1u)
    val lastName = string(index = 2u)
    val livingAddress = embed(
        index = 3u,
        dataModel = { Address }
    )
}
```

```kotlin
object Address : DataModel<Address>() {
  val street = string(index = 1u)
  val city = string(index = 2u)
}
```

## Creating property references

With compiled models Kotlin produces type strict property definitions. This means your IDE will
help to validate and autocomplete them.

Examples:
```kotlin
// Reference to firstName property
Person { firstName::ref }

// Reference to street through a Person
Person { livingAddress { street::ref } }
```

For deeper nesting, chain `{}` blocks and call `::ref` on the last property
```kotlin
Model { property { property { property { property { property::ref } } } } }
```

## Creating property references with String notation

When defined in YAML or JSON, property references can use a simple string format without including the model name.

To refer to the firstName on Person you use `firstName` and for the street on Address within Person you 
use `livingAddress.street`

## Referring to values of maps

It is also possible to refer to a value inside a map to filter or order:

```kotlin
// Model contains map with Time as a key
// Refer to the value at 12:23
Model { map refAt Time(12, 23) }
```

In string notation this becomes `map.@12:23`. Validation errors that refer to the key use `map.$12:23`.

### Wildcards for maps

Maps support wildcard references for both values and keys:

```kotlin
// Any key in the map
Model { map.refToAnyKey() }

// Any value in the map
Model { map.refToAnyValue() }
```

String notation:
- `map.~` = any map key
- `map.*` = any map value

## Referring to specific values in sets

Items in sets can be selected by value
```kotlin
// Model with a set property of Time
// Refer to the value at Time(12, 23)
Model { set refAt Time(12, 23) }
```

In string notation this is `set.$12:23`.

### Wildcards for sets

Sets support wildcard references to match any item value:

```kotlin
Model { set.refToAny() }
```

String notation:
- `set.*` = any set value

## Referring to index of lists

Items in lists can also be selected by index
```kotlin
// Model with list property
// Refer to the value at index 5
Model { list refAt 5 }
```

In string notation this is `list.@5`.
