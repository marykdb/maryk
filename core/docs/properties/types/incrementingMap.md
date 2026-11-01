# Incrementing Map Property
Stores items indexed by an auto-incrementing number.

See [properties page](../README.md) to see which property types it can contain for value. 
Property definitions need to be required and values can thus not be null.

- Kotlin Definition: `IncrementingMapDefinition<K, V>` 
    - K for type of comparable to autoincrement
    - V for type of value definition
- Kotlin Value: `Map`
- Maryk Yaml Definition: `IncMap`

## Usage options
- Value

## Validation Options
- `required` - default true
- `final` - default false
- `minSize` - default unset. Minimum size of map
- `maxSize` - default unset. Maximum size of map

## Other options
- `keyDefinition` - definition of keys contained in map
- `valueDefinition` - definition of values contained in map

## Examples

**Example of a Map property definition for use within a Model**
```kotlin
val orderNames by incrementingMap(
    index = 1u,
    required = false,
    final = true,
    keyNumberDescriptor = UInt32,
    valueDefinition = StringDefinition()
)
```

**Example of a separate Map property definition**
```kotlin
val def = IncrementingMapDefinition(
    required = false,
    final = true,
    keyNumberDescriptor = UInt32,
    valueDefinition = StringDefinition()
)
```

## Operations
Use `IncMapChange` with `addValues` to append values under generated keys.
Use `Change` on an `at(key)` reference to replace or remove an existing entry,
and a separate `Check` to compare values before changing them.

Automatic allocation supports `SInt8`, `SInt16`, `SInt32`, `SInt64`, `UInt8`,
`UInt16`, `UInt32`, and `UInt64`. Unsigned allocation starts at `1`. Signed
allocation starts at the descriptor's minimum value plus one, preserving the
existing reversed-storage ordering. Floating-point key definitions remain readable
and accept explicit keys; automatic allocation with `Float32` or `Float64` is rejected.

Example on a model with a map containing integers mapped to strings:
```kotlin

IncMapChange(
    Model.ref { incMap }.change(
        addValues = listOf(
            "a",
            "b"
        )
    )
)
```

## Storage Byte representation
Depends on the specific implementation. The values are stored in their representative byte 
representation.

## Transport Byte representation

Uses the [map transport encoding](map.md#transport-byte-representation).
Automatic key allocation changes how entries are created, not their wire representation.
