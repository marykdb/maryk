# Time
A Time Property which can be used to represent the time in hours, minutes and optionally
in seconds and milliseconds.

- Maryk Yaml Definition: **Time**
- Kotlin Definition : **TimeDefinition**
- Kotlin Value : **Time**

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
- fillWithNow - if true it will set the time with the current UTC time

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true
- precision - The precision to which the time is stored and transported. 
  SECONDS (default) or MILLIS. This value cannot be changed after storing first data.

**Example of a kotlin String definition**
```kotlin
val def = TimeDefinition(
    name = "meetingTime",
    index = 0,
    required = false,
    final = true,
    unique = true,
    minValue = Time(8, 30),
    maxValue = Time(18, 0),
    precision = TimePrecision.MILLIS,
    fillWithNow = true
)
```

## Storage Byte representation
It depends on the precision of the Time how it will be stored

- SECONDS - 3 byte integer counting the seconds from midnight (0:00am)
- MILLIS - 4 byte integer counting the milliseconds from midnight (0:00am)

## Transport Byte representation
The bytes are transported in a different way depending on the precision of time.

- SECONDS - VarInt integer counting the seconds from midnight
- MILLIS - VarInt integer counting the milliseconds from midnight 

## String representation
ISO8601 String: HH:MM:SS.SSS