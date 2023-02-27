# GeoPoint
Represents a geographical coordinate for a location. The value can contain up 
to 7 digits. This represents an accuracy of up to ~11mm or ~0.43 inch.

- Kotlin Definition: `GeoPointDefinition`
- Kotlin Value: `GeoPoint`
- Maryk Yaml Definition: `GeoPoint`

## Usage options
- Value
- Map key or value
- Inside List/Set

## Validation Options
- `required` - default true
- `final` - default false

## Other options
- `default` - the default value to be used if value was not set.

## Examples

**Example of a GeoPoint property definition for use within a Model its PropertyDefinitions**
```kotlin
val location by geoPoint(
    index = 1u,
    required = true,
    final = true,
    default = GeoPoint(52.0906448, 5.1212607)
)
```

**Example of a separate Enum property definition**
```kotlin
val def = GeoPointDefinition(
    required = true,
    final = true,
    default = GeoPoint(52.0906448, 5.1212607)
)
```

## Storage Byte representation
The values are encoded as 2 integers in full format each taking 4 bytes for a total
of 8 bytes. The integer is calculated by multiplying the latitude/longitude by 10000000
and is rounded so it is accurate to 7 digits.

## Transport Byte representation
The latitude and longitude value are combined into one Long by bitshifting the latitude as int
in the front of the long and the longitude in the back. This is encoded as a little endian
encoded long compatible with `fixed64` encoding in ProtoBuf. 

## String representation
The two doubles separated by a comma: `52.0906448,5.1212607`
