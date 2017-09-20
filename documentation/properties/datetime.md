# DateTime
A DateTime Property which can be used to represent the time in a date plus time.

- Maryk Yaml Definition: **DateTime** | **DateTime.Millis** 
- Kotlin Definition : **DateTimeDefinition**
- Kotlin Value: **DateTime**

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
- fillWithNow - if true it will set the dateTime with the current UTC time

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true
- precision - The precision to which the time is stored and transported. 
  SECONDS (default) or MILLIS. This value cannot be changed after storing first data.

**Example of a kotlin DateTime definition**
```kotlin
val def = DateTimeDefinition(
    name = "meetingDateTime",
    index = 0,
    required = true,
    final = true,
    unique = true,
    minValue = DateTime(Date(2017, 12, 1),Time(8, 30)),
    maxValue = DateTime(Date(2022, 12, 1),Time(18, 0)),
    precision = TimePrecision.MILLIS,
    fillWithNow = true
)
```

## Byte representation
It depends on the precision of the Time how it will be stored

- SECONDS - 7 bit integer counting the seconds from midnight January 1st 1970
- MILLIS - 9 bit integer with 7 bits counting the seconds from midnight January 1st 1970 
and 2 bits for the added milliseconds 

## String representation
ISO8601 String YYYY-MM-DDTHH:MM:SS.SSS
