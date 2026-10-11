# Fixed bytes
Represents a fixed-length byte array.

- Kotlin Definition: `FixedBytesDefinition`
- Kotlin Value: `Bytes` (`maryk.core.properties.types.Bytes`)
- Maryk Yaml Definition: `FixedBytes`

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
- `byteSize` - fixed bytes size

## Examples

**Example of a Fixed Bytes property definition for use within a Model**
```kotlin
val value by fixedBytes(
    index = 1u,
    byteSize = 4,
    required = false,
    final = true,
    unique = true,
    default = Bytes.ofHex("00010100"),
    minValue = Bytes.ofHex("00000000"),
    maxValue = Bytes.ofHex("01010000")
)
```

**Example of a separate Kotlin Fixed Bytes property definition**
```kotlin
val def = FixedBytesDefinition(
    required = false,
    final = true,
    unique = true,
    byteSize = 4,
    default = Bytes.ofHex("00010100"),
    minValue = Bytes.ofHex("00000000"),
    maxValue = Bytes.ofHex("01010000")
)
```

## Storage/Transport Byte representation
The byte array of the property. 
In transport bytes it is encoded as Length Delimited. 

## String representation
Base 64 representation of the bytes
