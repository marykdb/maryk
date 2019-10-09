# GeoPoint
Represents a geographical coordinate for a location. The value can contain up 
to 7 digits. This represents an accuracy of up to ~11mm or ~0.43 inch.

- Maryk Yaml Definition: `GeoPoint`
- Kotlin Definition: `GeoPointDefinition`
- Kotlin Value: `GeoPoint`

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

**Example of a YAML Enum property definition**
```yaml
!GeoPoint
  required: false
  final: true
  default: 52.0906448,5.1212607
```

**Example of a Kotlin Enum property definition**
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
The same format as storage bytes.

## String representation
The two doubles separated by a comma: `52.0906448,5.1212607`