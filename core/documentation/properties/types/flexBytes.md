# Flex bytes
Defines a property of a flexible bytes length.

- Maryk Yaml Definition: `FlexBytes`
- Kotlin Definition: `FlexBytesDefinition`
- Kotlin Value: `ByteArray`

## Usage options
- Value
- Map Value
- List

## Validation Options
- `required` - default true
- `final` - default false
- `unique` - default false
- `minSize` - minimum length of the byte array. Default unset.
- `maxSize` - maximum length of the byte array. Default unset.
- `minValue` - default false. Minimum value (Sort start value)
- `maxValue` - default false. Maximum value (Sort end value)

## Data options
- `indexed` - default false
- `searchable` - default true

**Example of a Kotlin Flex Bytes property definition**
```kotlin
val def = FlexBytesDefinition(
    required = false,
    final = true,
    unique = true,
    random = true,
    minSize = 2,
    maxSize = 5,
    minValue = Bytes.ofHex("0000"),
    maxValue = Bytes.ofHex("FFFFFFFFFF")
)
```

**Example of a YAML Flex Bytes property definition**
```yaml
!FlexBytes
  byteSize: 4
  required: false
  unique: false
  final: true
  minSize: 1
  maxSize: 6
  minValue: AA # Base64 value
  maxValue: //////////8 # Base64 value
  random: true
```

## Storage/Transport Byte representation
The byte array of the property.
In transport bytes it is encoded as Length Delimited.

## String representation
Base 64 representation of the bytes
