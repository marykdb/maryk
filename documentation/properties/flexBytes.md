# Flex bytes
Defines a property of a flexible bytes length.

- Maryk Yaml Definition: **FlexBytes**
- Kotlin Definition : **FlexBytesDefinition**
- Kotlin Value: **ByteArray**

## Usage options
- Value
- Map Value
- List

## Validation Options
- Required
- Final
- Unique

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true

**Example of a kotlin Flex Bytes definition**
```kotlin
val def = FlexBytesDefinition(
    name = "encodedValue",
    index = 0,
    required = true,
    final = true,
    unique = true,
    random = true
)
```

## Storage/Transport Byte representation
The byte array of the property.
In transport bytes it is encoded as Length Delimited.

## String representation
Base 64 representation of the bytes