# Map Property
Represents a map from keys to values.

See [properties page](../README.md) to see which property types it can contain for
as key and as value. Property definitions need to be required and values can thus not
be null.

- Kotlin Definition: `MapDefinition<K, V>` 
    - K for type of key definition 
    - V for type of value definition
- Kotlin Value: `Map`
- Maryk Yaml Definition: `Map`

## Usage options
- Value

## Validation Options
- `required` - default true
- `final` - default false
- `minSize` - default unset. Minimum size of map
- `maxSize` - default unset. Maximum size of map

## Other options
- `default` - the default value to be used if value was not set.
- `keyDefinition` - definition of keys contained in map
- `valueDefinition` - definition of values contained in map

## Examples

**Example of a Map property definition for use within a Model**
```kotlin
val mappedNames by map(
    index = 1u,
    required = false,
    final = true,
    keyDefinition = NumberDefinition(type = UInt32),
    valueDefinition = StringDefinition()
)
```

**Example of a separate Map property definition**
```kotlin
val def = MapDefinition(
    required = false,
    final = true,
    keyDefinition = NumberDefinition(type = UInt32),
    valueDefinition = StringDefinition()
)
```

## Operations
Maps can be applied with Map operations through `MapPropertyChange` to check
or change the contents. It can be defined with a map with `valuesToAdd` or a set of 
`keysToDelete`.

Example on a model with a map containing integers mapped to strings:
```kotlin
MapChange(
    Model { mapOfIntToString::ref }.change(
        valuesToAdd = mapOf(
            3 to "three",
            4 to "four"
        ),
        keysToDelete = setOf(1, 2)
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
