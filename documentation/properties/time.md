# Time
A Time Property which can be used to represent the time in hours, minutes and optionally
in seconds and milliseconds.

- Kotlin Definition : **TimeDefinition**
- Maryk Yaml Definition: **Time**

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
- fillWithNow - if true it will set the time with the current UTC time

## Data options
- precision - The precision to which the time is stored and transported. 
  SECONDS (default) or MILLIS. This value cannot be changed after storing first data.
- Index - Position in DataModel 
- Indexed - Default false
- Searchable - Default true

**Example of a kotlin String definition**
```kotlin
TimeDefinition(
    name = "meetingTime",
    index = 0,
    required = true,
    final = true,
    unique = true,
    minValue = Time(8, 30),
    maxValue = Time(18, 00),
    precision = TimePrecision.MILLIS,
    fillWithNow = true
)
```

## Byte representation
It depends on the precision of the Time how it will be stored

- SECONDS - 3 bit integer counting the seconds from midnight (0:00am)
- MILLIS - 4 bit integer counting the milliseconds from midnight (0:00am)

## String representation
In unoptimized mode it will be represented by an iso8601 String

In optimized mode it will use the integer counting either the seconds or milliseconds
from midnight.