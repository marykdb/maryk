# Map Property
A property to contain a list of items indexed by an incrementing number. 

See [properties page](../properties.md) to see which property types it can contain for value. 
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
Maps can be applied with Map operations through `IncMapChange` to check
or change the contents. It can be defined with a map with `valuesToAdd` or a set of 
`keysToDelete`.

Example on a model with a map containing integers mapped to strings:
```kotlin

IncMapChange(
    Model { incMap::ref }.change(
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
Maps are encoded as multiple entries of tag/value pairs with the tag referring to the index
of the map. The wire type is length delimited and the values are 2 tag/value pairs with the
first one with `tag=1` the key and secondly the value with `tag=2`.

Map encoding
``` T L Tk Vk Tv Vv  T L Tk Vk Tv Vv ```

- `T` is the tag index of the map
- `L` is length of encoded key value pair
- `Tk` is the tag for key and is 1
- `Vk` is the value of the key
- `Tv` is the tag for the value which is 2
- `Vv` is the encoded value of the value

(The encoded values could be encoded Length delimited and thus also contain lengths of the bytes)
