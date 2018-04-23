# String
Basic String property to represent text.

- Maryk Yaml Definition: **String**
- Kotlin Definition : **StringDefinition**
- Kotlin Value : **String**

## Usage options
- Value
- Map Value
- Inside List/Set

## Validation Options
- Required
- Final
- Unique
- RegEx - Regular expression to exactly match
- Minimum size - The min length of the string
- Maximum size - The max length of the string
- Minimum value - The min value (Sort start value)
- Maximum value - The max value (Sort end value)

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true

**Example of a kotlin String definition**
```kotlin
val def = StringDefinition(
    required = true,
    final = true,
    unique = true,
    minSize = 3,
    maxSize = 6,
    minValue = "aab",
    maxValue = "ddda",
    regEx = "^[abcd]{3,4}$"
)
```

## Storage/Transport Byte representation
Strings are stored as UTF-8 encoded bytes. With transport the LENGTH_DELIMITED wiretype is used

## String representation
String
