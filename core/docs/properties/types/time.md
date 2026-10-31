# Time
Represents the time of day in hours, minutes, seconds and optionally milliseconds or nanoseconds.

- Kotlin Definition: `TimeDefinition`
- Kotlin Value : `LocalTime`
- Maryk Yaml Definition: `Time`

## Usage options
- Value
- Map key or value
- Inside List/Set

## Validation Options
- `required` - default true
- `final` - default false
- `unique` - default false
- `minValue` - default unset. Minimum value
- `maxValue` - default unset. Maximum value

## Other options
- `default` - the default value to be used if value was not set.
- `precision` - The precision to which the time is stored and transported. 
  `SECONDS` (default), `MILLIS` or `NANOS`.

## Examples

**Example of a Time property definition for use within a Model**
```kotlin
val arrivalTime by time(
    index = 1u,
    required = false,
    final = true,
    unique = true,
    default = LocalTime(12, 0),
    minValue = LocalTime(8, 30),
    maxValue = LocalTime(18, 0),
    precision = TimePrecision.MILLIS
)
```

**Example of a separate Time property definition**
```kotlin
val def = TimeDefinition(
    required = false,
    final = true,
    unique = true,
    default = LocalTime(12, 0),
    minValue = LocalTime(8, 30),
    maxValue = LocalTime(18, 0),
    precision = TimePrecision.MILLIS
)
```

## Storage Byte representation
It depends on the precision of the Time how it will be stored

- `SECONDS` - 3 byte unsigned integer counting the seconds from midnight (0:00am)
- `MILLIS` - 4 byte unsigned integer counting the milliseconds from midnight (0:00am)
- `NANOS` - 6 byte unsigned long counting the nanoseconds from midnight (0:00am)

## Transport Byte representation
The bytes are transported in a different way depending on the precision of time.

- `SECONDS` - VarInt integer counting the seconds from midnight
- `MILLIS` - VarInt integer counting the milliseconds from midnight 
- `NANOS` - VarInt integer counting the nanoseconds from midnight 

## String representation
ISO8601 String: `HH:MM:SS.SSSSSSSSS`

Example: `20:47:22.825635486`

Native integer input represents whole seconds after midnight, from `0` through
`86399`. Fractional native numbers are rejected on JavaScript as well as JVM;
they are not truncated to another time.
