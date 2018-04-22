# Set Property
A property to contain a set of items. An item can only be added once and it contains 
no order.

See [properties page](properties.md) to see which property types it can contain.
Property definitions need to be required and values can thus not be null.

- Maryk Yaml Definition: **Set**
- Kotlin Definition : **SetDefinition<T>** T is for type of value definition
- Kotlin Value: **Set**

## Usage options
- Value

## Validation Options
- Required
- Final
- Minimum size
- Maximum size

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true
- valueDefinition

**Example of a kotlin Set definition**
```kotlin
val def = SetDefinition(
    required = true,
    final = true,
    valueDefinition = StringDefinition()
)
```

## Operations
Set can be applied with Set operations through `SetPropertyChange` to check
or change the contents. It can be defined with a set with `addValues` or a set with 
`deleteValues`. The current value can be compared against `valueToCompare`

Kotlin example on a model with a set containing strings.
```kotlin
Model.ref { setOfStrings }.change(
    addValues: setOf("three", "four"),
    deleteValues: setOf("one", "two"),
    valueToCompare: mapOf("one", "two")
)
```

## Byte representation
Depends on the specific implementation. The values are stored in their representative byte 
representation.

## Transport Byte representation
Sets can be encoded in 2 ways, normal tag/value pairs and packed mode. 

In tag/value pairs the values are encoded with a tag referring to the set and 
the value as their normal transport byte representation.

### Tag value pairs
``` T V  T V  T V  T V  T V ```

- T = Tag + wiretype
- V = Value encoded as LENGTH_DELIMITED or START_GROUP (Until end group)
 
In Packed encoding multiple values are encoded with one tag representing the specific 
set and a Length Delimiter. This is only available and is automatically applied for values
of BIT_32, BIT_64 or VAR_INT wiretype encoding since the sizes of each item are known.

### Packed encoding
``` T L V V V V V ```

- T = Tag + wiretype of Length Delimited
- L = VarInt with Length
- V = Value encoded as  BIT_32, BIT_64 or VAR_INT
