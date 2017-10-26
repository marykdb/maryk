# Boolean
Boolean true or false.

- Maryk Yaml Definition: **Boolean**
- Kotlin Definition : **BooleanDefinition**
- Kotlin Value: **Boolean**

## Usage options
- Value
- Map Key
- Map Value
- Inside List/Set

## Validation Options
- Required
- Final
- Unique
- Minimum value
- Maximum value

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true

**Example of a kotlin Boolean definition**
```kotlin
val def = BooleanDefinition(
    name = "editable",
    index = 0,
    required = true,
    final = true,
    unique = true,
    minValue = false,
    maxValue = true
)
```

## Storage and Transport Byte representation
0b0000 for false 0b0001 for true

## String representation
"true" or "false"