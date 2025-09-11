# Date
A Date Property which can be used to represent the date in years, month and date. The 
year is unsigned so it can contain negative dates

- Kotlin Definition: `DateDefinition`
- Kotlin Value: `LocalDate`
- Maryk Yaml Definition: `Date`

## Usage options
- Value
- Map Key or Value
- Inside List/Set

## Validation Options
- `required` - default true
- `final` - default false
- `unique` - default false
- `minValue` - default unset. Minimum value
- `maxValue` - default unset. Maximum value

## Other options
- `default` - the default value to be used if value was not set.

## Examples

**Example of a Date property definition for use within a Model**
```kotlin
val birthDate by date(
    index = 1u,
    required = false,
    final = true,
    unique = true,
    default = LocalDate(2018, 4, 30),
    minValue = LocalDate(1900, 1, 1),
    maxValue = LocalDate(2100, 1, 1)
)
```

**Example of a Date property definition**
```kotlin
val def = DateDefinition(
    required = false,
    final = true,
    unique = true,
    default = LocalDate(2018, 4, 30),
    minValue = LocalDate(1900, 1, 1),
    maxValue = LocalDate(2100, 1, 1)
)
```

## Storage Byte representation
The date is represented by a 4 byte Int counting the amount of days from January 1st 1970

## Transport Byte representation
The date is represented by a VarInt with the days since January 1st 1970

## String representation
ISO8601 String: `YYYY-MM-DD`

Example: `2018-04-24`
