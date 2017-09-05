#Number
Used to store numbers in specific formats. Signed floats and signed and 
unsigned integers are available.

- Kotlin Definition : **NumberDefinition**
- Maryk Yaml Definition: **UInt8 | UInt16 | UInt32 | UInt64 | Int8 | 
Int16 | Int32 | Int64 | Float32 | Float64**

## Types

### Unsigned Integers
- **UInt8** - 8 bit unsigned Integer 
    * Range: 0..255
- **UInt16** - 16 bit unsigned Integer 
    * Range: 0..65536
- **UInt32** - 32 bit unsigned Integer 
    * Range: 0..4294967296
- **UInt64** - 64 bit unsigned Integer 
    * Range: 0..18446744073709551615

### Signed Integers
- **Int8** - 8 bit signed Integer 
    * Range: -128..127
- **Int16** - 16 bit signed Integer 
    * Range: -32768..32767
- **Int32** - 32 bit signed Integer 
    * Range: -2147483648..2147483647
- **Int64** - 64 bit signed Integer 
    * Range: -9223372036854775808..9223372036854775807

### Signed Floats
- **Float32** - 32 bit single precision signed floating point 
    * Range: -Infinity..Infinity
- **Float64** - 64 bit double precision signed floating point 
    * Range: -Infinity..Infinity

## Usage options
- Value
- Map Key
- Map Value
- List

## Validation Options
- Required
- Final
- Unique
- RegEx - Regular expression to exactly match
- Minimum value
- Maximum value
- Random value

## Data options
- Index - Position in DataModel 
- Indexed - Default false
- Searchable - Default true

**Example of a kotlin Number definition**
```kotlin
NumberDefinition(
    name = "counter",
    index = 0,
    type = UInt32,
    required = true,
    final = true,
    unique = true,
    minValue = 32,
    maxValue = 1000000,
    random = true,
)
```

### Byte representation
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

//Int8:
1 == 0b1000_0001

//Int8:
-127 == 0b0000_0001

``` 