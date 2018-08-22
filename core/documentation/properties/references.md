# Property References

With a model like Person with a property called first name you sometimes want to query all the people which
are named `Smith`. To define a filter for this you need to refer to the property. In some databases they
call this the column. You can get a property reference from any [`DataModel`](../datamodel.md).   

These operations can be defined in Kotlin or any of the serialization formats Maryk supports. 

## Example models

This Person model has 2 top level fields (firstName, lastName) and an Embed named Address 
```yaml
name: Person
properties:
  ? 0: firstName
  : !String
  ? 1: lastName
  : !String
  ? 2: livingAddress
  : !Embed
    dataModel: Address
```

```yaml
name: Address
properties:
  ? 0: street
  : !String
  ? 1: city
  : !String
```

## Creating property references in Kotlin

With compiled models Kotlin produces type strict property definitions. This means your IDE will
help to validate and autocomplete them.

Examples:
```kotlin
// Reference to firstName property
Person.ref { firstName }

// Reference to street through a Person
Person { livingAddress ref { street } }
```

With deeper nesting of property reference you need to nest the `{}` blocks and call to the last one with ref
```kotlin
Model { property { property { property { property ref property } } } }
```

## Creating property references with String notation

Property references can be defined in a String format if they are defined in YAML or JSON. It is not 
needed to include the model name when defining those references.

To refer to the firstName on Person you use `firstName` and for the street on Address within Person you 
use `livingAddress.street`

## Referring to values of maps

It is also possible to refer a value of a map to filter or order:

```kotlin
// Model contains map with Time as a key
// Refer to the value at 12:23
Model { map refAt Time(12, 23) }
```

In string notation this can be defined as `map.@12:23`. Validation exceptions which refer to the key can be
of the following notation: `map.$12:23`

## Referring to specific values in sets

Items in sets can be selected by set value
```kotlin
// Model with a set property of Time
// Refer to the value at Time(12, 23)
Model { set refAt Time(12, 23) }
```

In string notation this can be defined as `set.$12:23`. 

## Referring to index of lists

Items in lists can also be selected by index
```kotlin
// Model with list property
// Refer to the value at index 5
Model { list refAt 5 }
```

In string notation this can be defined as `list.@5`. 
