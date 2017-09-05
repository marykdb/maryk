# Boolean
Boolean true or false.

- Kotlin Definition : **BooleanDefinition**
- Maryk Yaml Definition: **Boolean**

## Usage options
- Value
- Map Key
- Map Value
- List

## Validation Options
- Required
- Final
- Unique
- Minimum value
- Maximum value

## Data options
- Index - Position in DataModel 
- Indexed - Default false
- Searchable - Default true

**Example of a kotlin String definition**
```kotlin
BooleanDefinition(
    name = "editable",
    index = 0,
    required = true,
    final = true,
    unique = true,
    minValue = false,
    maxValue = true,
)
```

## Byte representation
0b0000 for false 0b0001 for true

## String representation
"true" or "false"