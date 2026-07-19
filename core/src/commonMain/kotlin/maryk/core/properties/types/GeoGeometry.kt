package maryk.core.properties.types

import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

const val MEAN_EARTH_RADIUS_METERS = 6_371_008.8

data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
)

/** Great-circle distance using the Haversine formula and mean Earth radius. */
fun GeoPoint.distanceTo(other: GeoPoint): Double {
    if (this == other) return 0.0

    val latitudeDelta = (other.latitude - latitude).toRadians()
    val longitudeDelta = (other.longitude - longitude).toRadians()
    val latitude1 = latitude.toRadians()
    val latitude2 = other.latitude.toRadians()
    val haversine = sin(latitudeDelta / 2).let { it * it } +
        cos(latitude1) * cos(latitude2) *
        sin(longitudeDelta / 2).let { it * it }
    return MEAN_EARTH_RADIUS_METERS * 2 *
        atan2(sqrt(haversine), sqrt((1.0 - haversine).coerceAtLeast(0.0)))
}

/** Inclusive geographic box. A west bound above east denotes antimeridian crossing. */
fun GeoPoint.isWithinBox(south: Double, west: Double, north: Double, east: Double): Boolean {
    require(south.isFinite() && north.isFinite()) { "Latitude bounds must be finite" }
    require(west.isFinite() && east.isFinite()) { "Longitude bounds must be finite" }
    require(south in -90.0..90.0 && north in -90.0..90.0) { "Latitude bounds must be within -90..90" }
    require(west in -180.0..180.0 && east in -180.0..180.0) { "Longitude bounds must be within -180..180" }
    require(south <= north) { "South bound must not exceed north bound" }

    val longitudeMatches = if (west <= east) {
        longitude in west..east
    } else {
        longitude >= west || longitude <= east
    }
    return latitude in south..north && longitudeMatches
}

/**
 * Inclusive point-in-polygon test. Polygon edges follow the shortest longitude path,
 * so polygons may cross the antimeridian without repeating or shifting vertices.
 */
fun GeoPoint.isWithinPolygon(vertices: List<GeoPoint>): Boolean {
    require(vertices.size >= 3) { "A polygon needs at least three vertices" }

    var previousLongitude: Double? = null
    val points = vertices.map { point ->
        var unwrappedLongitude = point.longitude.relativeTo(longitude)
        previousLongitude?.let { previous ->
            while (unwrappedLongitude - previous > 180.0) unwrappedLongitude -= 360.0
            while (previous - unwrappedLongitude > 180.0) unwrappedLongitude += 360.0
        }
        previousLongitude = unwrappedLongitude
        unwrappedLongitude to point.latitude
    }
    var inside = false
    for (index in points.indices) {
        val first = points[index]
        val second = points[(index + 1) % points.size]
        if (isOnLineSegment(0.0, latitude, first.first, first.second, second.first, second.second)) {
            return true
        }
        if ((first.second > latitude) != (second.second > latitude)) {
            val crossingLongitude = (second.first - first.first) *
                (latitude - first.second) / (second.second - first.second) + first.first
            if (crossingLongitude > 0.0) inside = !inside
        }
    }
    return inside
}

/** Smallest longitude arc which contains all [vertices], including antimeridian crossings. */
fun List<GeoPoint>.polygonBounds(): GeoBounds {
    require(size >= 3) { "A polygon needs at least three vertices" }
    val longitudes = map { if (it.longitude < 0.0) it.longitude + 360.0 else it.longitude }.sorted()
    var largestGap = -1.0
    var westIndex = 0
    for (index in longitudes.indices) {
        val next = if (index == longitudes.lastIndex) longitudes.first() + 360.0 else longitudes[index + 1]
        val gap = next - longitudes[index]
        if (gap > largestGap) {
            largestGap = gap
            westIndex = (index + 1) % longitudes.size
        }
    }
    return GeoBounds(
        south = minOf { it.latitude },
        west = longitudes[westIndex].toSignedLongitude(),
        north = maxOf { it.latitude },
        east = longitudes[(westIndex - 1 + longitudes.size) % longitudes.size].toSignedLongitude(),
    )
}

/** Conservative spherical bounding box for an inclusive radius query. */
fun GeoPoint.radiusBounds(radiusMeters: Double): GeoBounds {
    require(radiusMeters.isFinite() && radiusMeters >= 0.0) {
        "Radius must be a finite, non-negative number of metres"
    }
    if (radiusMeters >= PI * MEAN_EARTH_RADIUS_METERS) {
        return GeoBounds(-90.0, -180.0, 90.0, 180.0)
    }

    val angularDistance = radiusMeters / MEAN_EARTH_RADIUS_METERS
    val latitudeRadians = latitude.toRadians()
    val south = (latitudeRadians - angularDistance).coerceAtLeast(-PI / 2)
    val north = (latitudeRadians + angularDistance).coerceAtMost(PI / 2)
    if (south <= -PI / 2 || north >= PI / 2) {
        return GeoBounds(south.toDegrees(), -180.0, north.toDegrees(), 180.0)
    }

    val longitudeDelta = asin((sin(angularDistance) / cos(latitudeRadians)).coerceIn(-1.0, 1.0))
    return GeoBounds(
        south = south.toDegrees(),
        west = (longitude.toRadians() - longitudeDelta).normalizedLongitude().toDegrees(),
        north = north.toDegrees(),
        east = (longitude.toRadians() + longitudeDelta).normalizedLongitude().toDegrees(),
    )
}

/**
 * Stable geohash prefix with longitude and latitude bits interleaved.
 * Unused low bits of the final byte are zero.
 */
fun GeoPoint.geoHashBits(precisionBits: UInt = 32u): ByteArray {
    require(precisionBits in 1u..52u) { "Geohash precision must be within 1..52 bits" }

    var south = -90.0
    var north = 90.0
    var west = -180.0
    var east = 180.0
    val result = ByteArray((precisionBits.toInt() + 7) / 8)

    repeat(precisionBits.toInt()) { bitIndex ->
        val high = if (bitIndex % 2 == 0) {
            val middle = (west + east) / 2
            if (longitude >= middle) {
                west = middle
                true
            } else {
                east = middle
                false
            }
        } else {
            val middle = (south + north) / 2
            if (latitude >= middle) {
                south = middle
                true
            } else {
                north = middle
                false
            }
        }
        if (high) {
            val byteIndex = bitIndex / 8
            result[byteIndex] = (result[byteIndex].toInt() or (1 shl (7 - bitIndex % 8))).toByte()
        }
    }
    return result
}

private fun Double.toRadians() = this * kotlin.math.PI / 180.0
private fun Double.toDegrees() = this * 180.0 / kotlin.math.PI
private fun Double.relativeTo(center: Double): Double = (this - center).normalizedLongitudeDegrees()
private fun Double.toSignedLongitude(): Double = if (this > 180.0) this - 360.0 else this
private fun Double.normalizedLongitudeDegrees(): Double {
    var normalized = this
    while (normalized < -180.0) normalized += 360.0
    while (normalized > 180.0) normalized -= 360.0
    return normalized
}
private fun Double.normalizedLongitude(): Double {
    var normalized = this
    while (normalized < -PI) normalized += 2 * PI
    while (normalized > PI) normalized -= 2 * PI
    return normalized
}

private fun isOnLineSegment(
    pointX: Double,
    pointY: Double,
    startX: Double,
    startY: Double,
    endX: Double,
    endY: Double,
): Boolean {
    val deltaX = endX - startX
    val deltaY = endY - startY
    val cross = (pointX - startX) * deltaY - (pointY - startY) * deltaX
    if (kotlin.math.abs(cross) > 1e-10) return false
    val dot = (pointX - startX) * (pointX - endX) + (pointY - startY) * (pointY - endY)
    return dot <= 1e-10
}
