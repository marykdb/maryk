# Fixed bytes
Defines a property of a fixed bytes length.

- Maryk Yaml Definition: `FixedBytes`
- Kotlin Definition: `FixedBytesDefinition`
- Kotlin Value: `ByteArray`

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
- `random` - default false. True to auto create a random value

## Other options
- `default` - the default value to be used if value was not set.
- `indexed` - default false
- `searchable` - default true
- `byteSize` - fixed bytes size

## Examples

**Example of a YAML Fixed Bytes property definition**
```yaml
!FixedBytes
  byteSize: 4
  required: false
  unique: false
  final: true
  default: BBBBBBB # Base64 value
  minValue: AAAAAAA # Base64 value
  maxValue: //////8 # Base64 value
  random: true
```

**Example of a Kotlin Fixed Bytes property definition**
```kotlin
val def = FixedBytesDefinition(
    required = false,
    final = true,
    unique = true,
    byteSize = 4,
    default = byteArrayOf(0, 1, 1, 0),
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
