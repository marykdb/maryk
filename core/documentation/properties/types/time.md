# Time
A Time Property which can be used to represent the time in hours, minutes and optionally
in seconds and milliseconds.

- Maryk Yaml Definition: `Time`
- Kotlin Definition: `TimeDefinition`
- Kotlin Value : `Time`

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

## Other options
- `default` - the default value to be used if value was not set.
- `precision` - The precision to which the time is stored and transported. 
  `SECONDS` (default), `MILLIS` or `NANOS`.

## Examples

**Example of a YAML Time property definition**
```yaml
!Time
  precision: SECONDS
  required: false
  unique: false
  final: true
  default: 12:00:00
  minValue: 08:00:00
  maxValue: 17:59:59
```

**Example of a Kotlin String property definition for use within a Model its PropertyDefinitions**
```kotlin
val arrivalTime by time(
    index = 1u,
    required = false,
        final = true,
        unique = true,
        default = Time(12, 0),
        minValue = Time(8, 30),
        maxValue = Time(18, 0),
        precision = TimePrecision.MILLIS
)
```

**Example of a Kotlin String property definition**
```kotlin
val def = TimeDefinition(
    required = false,
    final = true,
    unique = true,
    default = Time(12, 0),
    minValue = Time(8, 30),
    maxValue = Time(18, 0),
    precision = TimePrecision.MILLIS
)
```

## Storage Byte representation
It depends on the precision of the Time how it will be stored

- `SECONDS` - 3 byte unsigned integer counting the seconds from midnight (0:00am)
- `MILLIS` - 4 byte unsigned integer counting the milliseconds from midnight (0:00am)
- `MILLIS` - 6 byte unsigned long counting the nanoseconds from midnight (0:00am)

## Transport Byte representation
The bytes are transported in a different way depending on the precision of time.

- `SECONDS` - VarInt integer counting the seconds from midnight
- `MILLIS` - VarInt integer counting the milliseconds from midnight 
- `NANOS` - VarInt integer counting the nanoseconds from midnight 

## String representation
ISO8601 String: `HH:MM:SS.SSSSSSSSS`

Example: `20:47:22.825635486`
