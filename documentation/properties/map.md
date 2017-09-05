# Map Property
A property to contain a map of items. 

See [properties page](properties.md) to see which property types it can contain for
as key and as value. Property definitions need to be required and values can thus not
be null.

- Maryk Yaml Definition: **Map**
- Kotlin Definition : **MapDefinition<K, V>** 
    - K for type of key definition 
    - V for type of value definition
- Kotlin Value: **Map**

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
- keyDefinition
- valueDefinition

**Example of a kotlin Set definition**
```kotlin
val def = ListDefinition(
    name = "listOfNames",
    index = 0,
    required = true,
    final = true,
    valueDefinition = StringDefinition()
)
```

## Byte representation
Depends on the specific implementation. The values are stored in their representative byte 
representation.
