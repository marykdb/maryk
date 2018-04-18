# List Property
A property to contain lists of items. The items are ordered and the same item can be
added multiple times.

See [properties page](properties.md) to see which property types it can contain.
Property definitions need to be required and values can thus not be null.

- Maryk Yaml Definition: **List**
- Kotlin Definition : **ListDefinition<T>** T is for type of value definition
- Kotlin Value: **List**

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

**Example of a kotlin List definition**
```kotlin
val def = ListDefinition(
    required = false,
    final = true,
    valueDefinition = StringDefinition()
)
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

- T = Tag + wiretype
- V = Value encoded as Length Delimited or Start Group (Until end group)
 
In Packed encoding multiple values are encoded with one tag representing the specific 
list and a Length Delimiter. This is only available and is automatically applied for values
of BIT_32, BIT_64 or VAR_INT wiretype encoding since the sizes of each item are known.

### Packed encoding
``` T L V V V V V ```

- T = Tag + wiretype of Length Delimited
- L = VarInt with Length
- V = Value encoded as  BIT_32, BIT_64 or VAR_INT
