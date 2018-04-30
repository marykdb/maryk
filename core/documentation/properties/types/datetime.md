# DateTime
A DateTime Property which can be used to represent the time in a date plus time.

- Maryk Yaml Definition: `DateTime` | `DateTime.Millis` 
- Kotlin Definition: `DateTimeDefinition`
- Kotlin Value: `DateTime`

## Usage options
- Value
- Map key or value
- Inside List/Set

## Validation Options
- `required` - default true
- `final` - default false
- `unique` - default false
- `minValue` - default false. Minimum value
- `maxValue` - default false. Maximum value
- `fillWithNow` - default false. If true it will set the dateTime with the current UTC time

## Other options
- `indexed` - default false
- `searchable` - default true
- `default` - the default value to be used if value was not set.
- `precision` - The precision to which the time is stored and transported. 
  `SECONDS` (default) or `MILLIS`. This value cannot be changed after storing first data.

## Examples

**Example of a Kotlin DateTime property definition**
```kotlin
val def = DateTimeDefinition(
    required = false,
    final = true,
    unique = true,
    default = DateTime(Date(2018, 10, 1),Time(9, 30)),
    minValue = DateTime(Date(2017, 12, 1),Time(8, 30)),
    maxValue = DateTime(Date(2022, 12, 1),Time(18, 0)),
    precision = TimePrecision.MILLIS,
    fillWithNow = true
)
```

**Example of a YAML DateTime property definition**
```yaml
!DateTime
  precision: SECONDS
  required: false
  unique: false
  final: true
  default: 2018-04-30T22:22:22
  minValue: 1900-01-01T00:00:00
  maxValue: 2099-12-31T23:59:59
  fillWithNow: true
```

## Storage Byte representation
It depends on the precision of the Time how it will be stored

- `SECONDS` - 7 byte integer counting the seconds from midnight January 1st 1970
- `MILLIS` - 9 byte integer with 7 bits counting the seconds from midnight January 1st 1970 
and 2 bits for the added milliseconds

## Transport Byte representation
The bytes are transported in a different way depending on the precision of time.

- `SECONDS` - VarInt integer counting the seconds from midnight January 1st 1970
- `MILLIS` - VarInt integer counting the milliseconds from midnight January 1st 1970 

## String representation
ISO8601 String YYYY-MM-DDTHH:MM:SS.SSS
