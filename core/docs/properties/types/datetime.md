# DateTime
Represents a date combined with a time using `kotlinx.datetime.LocalDateTime`.
It carries no time zone; byte encoding interprets the local fields in UTC.
Use an explicit application policy when converting zoned times or instants.

- Kotlin Definition: `DateTimeDefinition`
- Kotlin Value: `LocalDateTime`
- Maryk Yaml Definition: `DateTime`

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

**Example of a DateTime property definition for use within a Model**
```kotlin
val startDateTime by dateTime(
    index = 1u,
    required = false,
    final = true,
    unique = true,
    default = LocalDateTime(LocalDate(2018, 10, 1), LocalTime(9, 30)),
    minValue = LocalDateTime(LocalDate(2017, 12, 1), LocalTime(8, 30)),
    maxValue = LocalDateTime(LocalDate(2022, 12, 1), LocalTime(18, 0)),
    precision = TimePrecision.MILLIS
)
```

**Example of a separate Kotlin DateTime property definition**
```kotlin
val def = DateTimeDefinition(
    required = false,
    final = true,
    unique = true,
    default = LocalDateTime(LocalDate(2018, 10, 1), LocalTime(9, 30)),
    minValue = LocalDateTime(LocalDate(2017, 12, 1), LocalTime(8, 30)),
    maxValue = LocalDateTime(LocalDate(2022, 12, 1), LocalTime(18, 0)),
    precision = TimePrecision.MILLIS
)
```

## Storage Byte representation
It depends on the precision of the Time how it will be stored

- `SECONDS` - 7 byte integer counting the seconds from midnight January 1st 1970
- `MILLIS` - 9 byte combined integer with 7 bytes counting the seconds from midnight January 1st 1970 and 2 bytes for the milliseconds
- `NANOS` - 11 byte integer with 7 bytes counting the seconds from midnight January 1st 1970 and 4 bytes for the nanoseconds

## Transport Byte representation
The bytes are transported in a different way depending on the precision of time.

- `SECONDS` - VarInt integer counting the seconds from midnight January 1st 1970
- `MILLIS` - VarInt integer counting the milliseconds from midnight January 1st 1970
- `NANOS` - a length-delimited message with `int64 seconds = 1` and
  `uint32 nanos = 2`, using UTC epoch seconds and 0..999,999,999 nanoseconds.
  Readers also accept the legacy scalar VAR_INT seconds/nanos pair when the
  NANOS definition is known; writers always use the framed message. See
  [ProtoBuf compatibility](../../serialization/protobuf.md#nanos-datetime-compatibility).

## String representation
ISO8601 String YYYY-MM-DDTHH:MM:SS.SSSSSSSSS
