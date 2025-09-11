# Flex bytes
Defines a property of a flexible bytes length.

- Kotlin Definition: `FlexBytesDefinition`
- Kotlin Value: `ByteArray`
- Maryk Yaml Definition: `FlexBytes`

## Usage options
- Value
- Map Key or Value
- List

## Validation Options
- `required` - default true
- `final` - default false
- `unique` - default false
- `minSize` - minimum length of the byte array. Default unset.
- `maxSize` - maximum length of the byte array. Default unset.
- `minValue` - default false. Minimum value (Sort start value)
- `maxValue` - default false. Maximum value (Sort end value)

## Other options
- `default` - the default value to be used if value was not set.

## Examples

**Example of a Flex Bytes property definition for use within a Model**
```kotlin
val value by flexBytes(
    index = 1u,
    required = false,
    final = true,
    unique = true,
    minSize = 2,
    maxSize = 5,
    default = Bytes.ofHex("1111"),
    minValue = Bytes.ofHex("0000"),
    maxValue = Bytes.ofHex("FFFFFFFFFF")
)
```

**Example of a separate Flex Bytes property definition**
```kotlin
val def = FlexBytesDefinition(
    required = false,
    final = true,
    unique = true,
    minSize = 2,
    maxSize = 5,
    default = Bytes.ofHex("1111"),
    minValue = Bytes.ofHex("0000"),
    maxValue = Bytes.ofHex("FFFFFFFFFF")
)
```

## Storage/Transport Byte representation
The byte array of the property.
In transport bytes it is encoded as Length Delimited.

## String representation
Base 64 representation of the bytes
