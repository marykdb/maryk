# GeoPoint
Represents a geographical coordinate. Precision is up to 7 digits (~11 mm / 0.43 inch).
Coordinates must be finite and within latitude `-90..90` and longitude
`-180..180`.

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

**Example of a GeoPoint property definition for use within a Model**
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

## Spatial filtering

```kotlin
GeoWithinBox(Location.location.ref(), 52.0, 5.0, 52.2, 5.2)

GeoWithinRadius(
    Location.location.ref(),
    center = GeoPoint(52.0907, 5.1214),
    radiusMeters = 5_000.0,
)
```

Both filters include their boundary. A box with `west > east` crosses the
antimeridian. Radius matching uses exact post-filtering with the Haversine
formula and mean Earth radius `6,371,008.8 m`.

For indexed candidate scans, add a geohash index:

```kotlin
indexes = {
    listOf(GeoHash(Location.location.ref(), precisionBits = 32u))
}
```

## Complete indexed query

With a Maryk data store named `store`, run this inside a coroutine:

```kotlin
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.geoPoint
import maryk.core.properties.definitions.index.GeoHash
import maryk.core.properties.types.GeoPoint
import maryk.core.query.filters.GeoWithinRadius
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan

object Place : RootDataModel<Place>(
    indexes = { listOf(GeoHash(Place.location.ref(), precisionBits = 32u)) },
) {
    val location by geoPoint(index = 1u)
}

store.execute(
    Place.add(Place.create {
        location with GeoPoint(52.0907, 5.1214)
    }),
)

val nearby = store.execute(
    Place.scan(
        where = GeoWithinRadius(
            Place.location.ref(),
            center = GeoPoint(52.0907, 5.1214),
            radiusMeters = 5_000.0,
        ),
    ),
)
```

`nearby.values` contains only points inside the requested radius. The GeoHash
index narrows candidates; the radius predicate performs the exact final check.

## Polygon geofences

Use `GeoWithinPolygon` to match a point against one closed geofence. Supply at
least three vertices; the final edge back to the first vertex is implicit.

```kotlin
GeoWithinPolygon(
    Place.location.ref(),
    GeoPoint(52.0, 5.0),
    GeoPoint(52.0, 5.2),
    GeoPoint(52.2, 5.2),
    GeoPoint(52.2, 5.0),
)
```

Polygon edges and vertices are inclusive. Edges take the shortest longitude
path, so a polygon can cross the antimeridian. This filter targets a `GeoPoint`
property; holes, multipolygons, stored shapes, and shape-to-shape relations are
not supported. A compatible GeoHash index uses the polygon bounds for candidate
scanning and exact point-in-polygon matching removes false positives.

Precision ranges from 1 through 52 bits. Planning uses byte-aligned prefixes,
reducing precision until no more than 256 candidate cells are scanned, then
applies the exact spatial predicate. Geohash order is cell order, not distance
or nearest-neighbour order. Without a compatible index, spatial filters follow
the normal `allowTableScan` policy.

Precision below 8 bits cannot form a byte-aligned prefix, so it scans the full
geohash index rather than narrowing candidates.

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
