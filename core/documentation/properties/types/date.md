# Date
A Date Property which can be used to represent the date in years, month and date. The 
year is unsigned so it can contain negative dates

- Maryk Yaml Definition: `Date`
- Kotlin Definition: `DateDefinition`
- Kotlin Value: `Date`

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

**Example of a YAML Date property definition**
```yaml
!Date
  required: false
  unique: false
  final: true
  default: 2018-04-30
  minValue: 1900-01-01
  maxValue: 2100-01-01
```

**Example of a Kotlin Date property definition for use within a Model its PropertyDefinitions**
```kotlin
val birthDate by date(
    index = 1u,
    required = false,
    final = true,
    unique = true,
    default = Date(2018, 4, 30),
    minValue = Date(1900, 1, 1),
    maxValue = Date(2100, 1, 1)
)
```

**Example of a Kotlin Date property definition**
```kotlin
val def = DateDefinition(
    required = false,
    final = true,
    unique = true,
    default = Date(2018, 4, 30),
    minValue = Date(1900, 1, 1),
    maxValue = Date(2100, 1, 1)
)
```

## Storage Byte representation
The date is represented by a 4 byte Int counting the amount of days from January 1st 1970

## Transport Byte representation
The date is represented by a VarInt with the days since January 1st 1970

## String representation
ISO8601 String: `YYYY-MM-DD`

Example: `2018-04-24`
