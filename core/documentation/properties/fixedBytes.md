# Fixed bytes
Defines a property of a fixed bytes length.

- Maryk Yaml Definition: **FixedBytes**
- Kotlin Definition : **FixedBytesDefinition**
- Kotlin Value: ByteArray

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
- random - true to auto create a random value

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true

**Example of a kotlin Fixed Bytes definition**
```kotlin
val def = FixedBytesDefinition(
    required = false,
    final = true,
    unique = true,
    byteSize = 4,
    minValue = byteArrayOf(0, 0, 0, 0),
    maxValue = byteArrayOf(1, 1, 0, 0),
    random = true
)
```

## Storage/Transport Byte representation
The byte array of the property. 
In transport bytes it is encoded as Length Delimited. 

## String representation
Base 64 representation of the bytes
