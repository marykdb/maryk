# String
Basic String property to represent text.

- Kotlin Definition : **StringDefinition**
- Maryk Yaml Definition: **String**

## Usage options
- Value
- Map Value
- List

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
- Index - Position in DataModel 
- Indexed - Default false
- Searchable - Default true

**Example of a kotlin String definition**
```kotlin
StringDefinition(
    name = "test",
    index = 0,
    required = true,
    final = true,
    unique = true,
    minSize = 3,
    maxSize = 6,
    minValue = "aab",
    maxValue = "ddda",
    regEx = "^[abcd]{3,4}$",
)
```

## Byte representation
UTF-8 byte conversion

## String representation
String