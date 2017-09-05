# Date
A Date Property which can be used to represent the date in years, month and date. The 
year is unsigned so it can contain negative dates

- Kotlin Definition : **DateDefinition**
- Maryk Yaml Definition: **Date**

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
- fillWithNow - if true it will set the date with the current UTC date

## Data options
- Index - Position in DataModel 
- Indexed - Default false
- Searchable - Default true

**Example of a kotlin String definition**
```kotlin
DateDefinition(
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
It will be represented by a 8 bit long counting the amount of days from January 1st 1970

## String representation
In unoptimized mode it will be represented by an iso8601 String

In optimized mode it will use the long with the days since January 1st 1970