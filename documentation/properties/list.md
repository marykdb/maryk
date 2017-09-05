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
    name = "listOfNames",
    index = 0,
    required = true,
    final = true,
    valueDefinition = StringDefinition()
)
```

## Byte representation
Depends on the specific implementation. The values are stored in their representative
byte representation.
