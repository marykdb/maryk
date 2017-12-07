# Map Property
A property to contain a map of items. 

See [properties page](properties.md) to see which property types it can contain for
as key and as value. Property definitions need to be required and values can thus not
be null.

- Maryk Yaml Definition: **Map**
- Kotlin Definition : **MapDefinition<K, V>** 
    - K for type of key definition 
    - V for type of value definition
- Kotlin Value: **Map**

## Usage options
- Value

## Validation Options
- Required
- Final
- Minimum size
- Maximum size


## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true
- keyDefinition
- valueDefinition

**Example of a kotlin Map definition**
```kotlin
val def = MapDefinition(
    name = "mapOfIntString",
    index = 0,
    required = false,
    final = true,
    keyDefinition = NumberDefinition(type = UInt32),
    valueDefinition = StringDefinition()
)
```

## Storage Byte representation
Depends on the specific implementation. The values are stored in their representative byte 
representation.

## Transport Byte representation
Maps are encoded as multiple entries of tag/value pairs with the tag referring to the index
of the map. The wire type is length delimited and the values are 2 tag/value pairs with the
first one with tag=1 the key and secondly the value with tag=2.

Map encoding
``` T L Tk Vk Tv Vv  T L Tk Vk Tv Vv ```

- T is the tag index of the map
- L is length of encoded key value pair
- Tk is the tag for key and is 1
- Vk is the value of the key
- Tv is the tag for the value which is 2
- Vv is the encoded value of the value

(The encoded values could be encoded Length delimited and thus also contain lengths of the bytes)
