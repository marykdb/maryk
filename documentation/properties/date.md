# Date
A Date Property which can be used to represent the date in years, month and date. The 
year is unsigned so it can contain negative dates

- Maryk Yaml Definition: **Date**
- Kotlin Definition : **DateDefinition**
- Kotlin Value: **Date**


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
- fillWithNow - if true it will set the date with the current UTC date

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true

**Example of a kotlin Date definition**
```kotlin
val def = DateDefinition(
    name = "dateOfBirth",
    index = 0,
    required = true,
    final = true,
    unique = true,
    minValue = false,
    maxValue = true,
    fillWithNow = true
)
```

## Byte representation
It will be represented by a 8 byte long counting the amount of days from January 1st 1970

## String representation
ISO8601 String: YYYY-MM-DD
