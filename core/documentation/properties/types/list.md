# List Property
A property to contain lists of items. The items are ordered and the same item can be
added multiple times.

See [properties page](../properties.md) to see which property types it can contain.
Property definitions need to be required and values can thus not be null.

- Maryk Yaml Definition: `List`
- Kotlin Definition: `ListDefinition<T>` T is for type of value definition
- Kotlin Value: `List`

## Usage options
- Value

## Validation Options
- `required` - default true
- `final` - default false
- `minSize` - default unset. Minimum size of list
- `maxSize` - default unset. Maximum size of list

## Other options
- `default` - the default value to be used if value was not set.
- `valueDefinition` - definition of values contained

## Examples

**Example of a YAML List property definition**
```yaml
!List
  valueDefinition: !String
  required: false
  final: true
  minSize: 1
  maxSize: 10
```

**Example of a Kotlin List property definition for use within a Model its PropertyDefinitions**
```kotlin
val names by list(
    index = 1u,
    required = false,
    final = true,
    valueDefinition = StringDefinition()
)
```

**Example of a Kotlin List property definition**
```kotlin
val def = ListDefinition(
    required = false,
    final = true,
    valueDefinition = StringDefinition()
)
```

## Operations
List can be applied with List operations through `ListPropertyChange` to check
or change their contents. It can be defined with a set with `addValuesToEnd` or 
a list with `deleteValues`. It can also operate with the value index by
 a map with `addValuesAtIndex` or a list with `deleteAtIndex`. 

The current value can be compared against `valueToCompare`

**Example on a model with a list containing strings.**

Maryk Yaml:
```yaml
!ListChange
  listOfStrings:
    addValuesToEnd:
    - three 
    - four
    deleteValues: [one, two]
```
Kotlin:
```kotlin
ListChange(
    Model { listOfStrings::ref }.change(
        addValuesToEnd = listOf("three", "four"),
        deleteValues = listOf("one", "two")
    )
)
```

**Example with indexed operations on a model with a list containing strings.**

Kotlin:
```kotlin
ListChange(
    Model { listOfStrings::ref }.change(
        addValuesAtIndex = mapOf(
            0 to "three", 
            0 to "four"
        ),
        deleteAtIndex = setOf(1, 2)
    )
)
```

Maryk Yaml:
```yaml
!ListChange
  listOfStrings:
    addValuesAtIndex:
      0: three 
      1: four
    deleteAtIndex: [1, 2]
```

## Storage Byte representation
Depends on the specific implementation. The values are stored in their representative
byte representation.

## Transport Byte representation
Lists can be encoded in 2 ways, normal tag/value pairs and packed mode. 

In tag/value pairs the values are encoded with a tag referring to the list and 
the value as their normal transport byte representation.

### Tag value pairs
``` T V  T V  T V  T V  T V ```

- `T` = Tag + wiretype
- `V` = Value encoded as Length Delimited or Start Group (Until end group)
 
In Packed encoding multiple values are encoded with one tag representing the specific 
list and a Length Delimiter. This is only available and is automatically applied for values
of `BIT_32`, `BIT_64` or `VAR_INT` wiretype encoding since the sizes of each item are known.

### Packed encoding
``` T L V V V V V ```

- `T` = Tag + wiretype of Length Delimited
- `L` = VarInt with Length
- `V` = Value encoded as `BIT_32`, `BIT_64` or `VAR_INT`
