# Number
Used to store numbers in specific formats. Signed and unsigned integers as well as floating‑point types are available.

Choose [Decimal](decimal.md) when a value must be exact at a fixed scale, such
as money or a rate, or when a whole number needs more than the built-in 64-bit
integer range. `Decimal(scale = 0u)` is its exact whole-number form.

- Kotlin Definition: `NumberDefinition`
- Kotlin Value: `UByte` | `UShort` | `UInt` | `ULong` | `Byte` |
                `Short` | `Int` | `Long` | `Float` | `Double`
- Maryk Yaml Definition: `UInt8` | `UInt16` | `UInt32` | `UInt64` | `SInt8` |
`SInt16` | `SInt32` | `SInt64` | `Float32` | `Float64`

## Types

### Unsigned Integers
- `UInt8` - 8 bit Unsigned Integer
    * Range: `0..255`
- `UInt16` - 16 bit Unsigned Integer
    * Range: `0..65535`
- `UInt32` - 32 bit Unsigned Integer
    * Range: `0..4294967295`
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
- `minValue` - default unset. Minimum value
- `maxValue` - default unset. Maximum value

## Other options
- `default` - the default value to be used if value was not set.
- `type` - [type of number](#types).
- `reversedStorage` - reverses sortable storage bytes for descending index storage.

`NumberDefinition` implements the arithmetic, random-value, and reversible
storage capabilities. This allows `Sum`, `Average`, and `Stats` to work without
depending on the concrete number definition class.

## Examples

**Example of a Number property definition for use within a Model**
```kotlin
val count by number(
    index = 1u,
    type = UInt64,
    required = true,
    final = true,
    unique = true,
    default = 42uL,
    minValue = 32uL,
    maxValue = 1000000uL
)
```

**Example of a separate Number property definition**
```kotlin
val def = NumberDefinition(
    type = UInt32,
    required = true,
    final = true,
    unique = true,
    default = 42u,
    minValue = 32u,
    maxValue = 1000000u
)
```

## Storage Byte representation
All numbers are encoded to a fixed length byte format fitting their type.
For example 4 bytes for Int32 and 1 for Int8. Integer storage bytes follow
natural numeric order. Signed integers switch the sign bit so negative
numbers come before positive ones.

`Float32` and `Float64` use the same reversible ordered representation for
ordinary values and index components: complement negative IEEE bits and switch
the sign bit for nonnegative values, then write unsigned big-endian bytes. This
orders negatives, signed zeros, positives and infinities numerically. Ordinary
storage preserves raw NaN payload bits; indexes normalize NaNs for Kotlin numeric
comparison. Index writers encode directly from numeric values.

Persistent stores perform one opening migration from the released, unversioned
layout (legacy format 1) to storage format 2. The migration converts affected
current/historical values, collection qualifiers, packed value objects, unique
lookups and indexes. Unchanged cells and fixed-width non-floating indexes are
kept. Record IDs, references and timestamps remain unchanged under the supported
index/field-only upgrade. Existing primary keys whose bytes change require an
explicit key/reference migration and fail preflight. Completion is recorded in
store metadata; later opens skip conversion, with no per-record versions.

Float/Double Protobuf scalar encoding stays unchanged. Packed value objects
containing floating-point fields use the new bytes in serialization too; old
IndexedDB logs are converted at opening, backup format 3 rejects older backups,
and affected remote models require a matching value-object format.

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
 - `Float32` Encoded as 32-bit
 - `Float64` Encoded as 64-bit
