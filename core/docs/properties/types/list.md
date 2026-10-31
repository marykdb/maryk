# List Property
Stores an ordered list of items where duplicates are allowed.

See [properties page](../README.md) to see which property types it can contain.
Property definitions need to be required and values can thus not be null.

- Kotlin Definition: `ListDefinition<T>` T is for type of value definition
- Kotlin Value: `List`
- Maryk Yaml Definition: `List`

## Usage options
- Value

## Validation Options
- `required` - default true
- `final` - default false
- `minSize` - default unset. Minimum size of list
- `maxSize` - default unset. Maximum size of list

A final list can be initialized when it is absent. A delta that changes an
already initialized final list is rejected; a no-op preserves its revision.

## Other options
- `default` - the default value to be used if value was not set.
- `valueDefinition` - definition of values contained

## Examples

**Example of a List property definition for use within a Model**
```kotlin
val names by list(
    index = 1u,
    required = false,
    final = true,
    valueDefinition = StringDefinition()
)
```

**Example of a separate List property definition**
```kotlin
val def = ListDefinition(
    required = false,
    final = true,
    valueDefinition = StringDefinition()
)
```

## Operations
Use `ListChange` with `ListValueChanges` to remove matching values, insert at
unsigned indexes, or append values. Operations run in that order: removals,
indexed insertions, then appends. Insertion indexes refer to the list after
removals and any preceding insertion. Use a separate `Check` to compare the
complete current list before changing it.

Each requested `deleteValues` entry removes its first remaining occurrence.
For example, deleting `"one"` once from `["one", "one", "two"]` leaves
`["one", "two"]`. Repeat the requested value to remove another occurrence.
Embedded and typed list items retain their complete contents when insertions or
deletions shift their positions.

```kotlin
ListChange(
    Model.ref { listOfStrings }.change(
        deleteValues = listOf("one", "two"),
        addValuesToEnd = listOf("three", "four")
    )
)
```

Indexed insertions use `UInt` indexes:

```kotlin
ListChange(
    Model.ref { listOfStrings }.change(
        addValuesAtIndex = mapOf(
            0u to "three",
            1u to "four"
        )
    )
)
```

For replacement or deletion of one item, use its reference:

```kotlin
Change(Model.ref { listOfStrings.at(0u) } with "replacement")
Change(Model.ref { listOfStrings.at(0u) } with null)
```

## Indexing list values

List references like `at(index)` and `any()` (`list.*`) can be used in filters and changes.
They are not indexable in `RootDataModel` index definitions.

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
