# Number
Used to store numbers in specific formats. Signed floats and signed and 
unsigned integers are available.

- Kotlin Definition: `NumberDefinition`
- Kotlin Value: `UInt8` | `UInt16` | `UInt32` | `UInt64` | `Byte` | 
                `Short` | `Int` | `Long` | `Float` | `Double`
- Maryk Yaml Definition: `UInt8` | `UInt16` | `UInt32` | `UInt64` | `Int8` | 
`Int16` | `Int32` | `Int64` | `Float32` | `Float64`

## Types

### Unsigned Integers
- `UInt8` - 8 bit Unsigned Integer 
    * Range: `0..255`
- `UInt16` - 16 bit Unsigned Integer 
    * Range: `0..65536`
- `UInt32` - 32 bit Unsigned Integer 
    * Range: `0..4294967296`
- `UInt64` - 64 bit Unsigned Integer 
    * Range: `0..18446744073709551615`

### Signed Integers
- `SInt8` - 8 bit Signed Integer 
    * Range: `-128..127`
- `SInt16` - 16 bit Signed Integer 
    * Range: `-32768..32767`
- `SInt32` - 32 bit signed Integer 
    * Range: `-2147483648..2147483647`
- `SInt64` - 64 bit Signed Integer 
    * Range: `-9223372036854775808..9223372036854775807`

### Signed Floats
- `Float32` - 32 bit single precision signed floating point 
    * Range: `-Infinity..Infinity`
- `Float64` - 64 bit double precision signed floating point 
    * Range: -`Infinity..Infinity`

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

## Other options
- `default` - the default value to be used if value was not set.
- `type` - [type of number](#types). 

## Examples

**Example of a Number property definition for use within a Model**
```kotlin
val count by number(
    index = 1u,
    type = UInt64,
    required = true,
    final = true,
    unique = true,
    default = 42,
    minValue = 32,
    maxValue = 1000000
)
```

**Example of a separate Number property definition**
```kotlin
val def = NumberDefinition(
    type = UInt32,
    required = true,
    final = true,
    unique = true,
    default = 42,
    minValue = 32,
    maxValue = 1000000
)
```

## Storage Byte representation
All numbers are encoded to a fixed length byte format fitting their type. 
For example 4 bytes for Int32 and 1 for Int8. All numbers are encoded in
an order fitting their natural order. For signed numbers this means that 
the sign byte is switched so negative numbers come before positive ones.

Examples:

```
//UInt8:
1 == 0b0000_0001

//UInt8:
129 == 0b1000_0001

//SInt8:
1 == 0b1000_0001

//SInt8:
-127 == 0b0000_0001

``` 

## Transport Byte representation
The numbers are differently encoded depending on their type.

 - `UInt8` `UInt16` `UInt32` `UInt64` Encoded as VarInt as unsigned
 number.
 - `SInt8` `SInt16` `SInt32` `SInt64` Encoded as VarInt zigzaging
 between positive and negative numbers. This means 0 as 0, -1 as 1, 1 as 2,
 -2 as 3 etc. This way small positive and negative numbers take less space
 - `Float32` Encoded as 64-bit
 - `Float64` Encoded as 32-bit
