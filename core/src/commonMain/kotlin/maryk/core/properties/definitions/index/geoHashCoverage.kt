package maryk.core.properties.definitions.index

import maryk.core.properties.types.GeoBounds
import kotlin.math.floor

const val MAX_GEOHASH_COVER_CELLS = 256

/**
 * Cover [bounds] using byte-aligned geohash prefixes compatible with [GeoHash].
 * Precision is reduced until at most [maxCells] prefixes are needed.
 */
fun GeoHash.coveringPrefixes(
    bounds: GeoBounds,
    maxCells: Int = MAX_GEOHASH_COVER_CELLS,
): List<ByteArray> {
    require(maxCells > 0) { "Maximum cells must be positive" }
    require(bounds.south <= bounds.north) { "South bound must not exceed north bound" }

    var precision = precisionBits.toInt() / 8 * 8
    while (precision >= 8) {
        val latitudeBits = precision / 2
        val longitudeBits = precision - latitudeBits
        val latitudeCells = 1L shl latitudeBits
        val longitudeCells = 1L shl longitudeBits
        val southIndex = coordinateIndex(bounds.south, -90.0, 90.0, latitudeCells)
        val northIndex = coordinateIndex(bounds.north, -90.0, 90.0, latitudeCells)
        val longitudeRanges = longitudeRanges(bounds.west, bounds.east, longitudeCells)
        val count = longitudeRanges.sumOf { it.last - it.first + 1 } *
            (northIndex - southIndex + 1)

        if (count <= maxCells) {
            return buildList(count.toInt()) {
                for (latitudeIndex in southIndex..northIndex) {
                    longitudeRanges.forEach { longitudeRange ->
                        for (longitudeIndex in longitudeRange) {
                            add(interleave(longitudeIndex, longitudeBits, latitudeIndex, latitudeBits, precision))
                        }
                    }
                }
            }
        }
        precision -= 8
    }
    return emptyList()
}

private fun longitudeRanges(west: Double, east: Double, cellCount: Long): List<LongRange> {
    val westIndex = coordinateIndex(west, -180.0, 180.0, cellCount)
    val eastIndex = coordinateIndex(east, -180.0, 180.0, cellCount)
    return if (west <= east) {
        listOf(westIndex..eastIndex)
    } else {
        listOf(westIndex until cellCount, 0L..eastIndex)
    }
}

private fun coordinateIndex(value: Double, minimum: Double, maximum: Double, cellCount: Long): Long {
    require(value.isFinite() && value in minimum..maximum) { "Coordinate is outside its valid bounds" }
    if (value == maximum) return cellCount - 1
    return floor((value - minimum) / (maximum - minimum) * cellCount).toLong()
        .coerceIn(0, cellCount - 1)
}

private fun interleave(
    longitudeIndex: Long,
    longitudeBits: Int,
    latitudeIndex: Long,
    latitudeBits: Int,
    precision: Int,
): ByteArray {
    val output = ByteArray(precision / 8)
    var longitudeBit = longitudeBits - 1
    var latitudeBit = latitudeBits - 1
    repeat(precision) { outputBit ->
        val bit = if (outputBit % 2 == 0) {
            (longitudeIndex shr longitudeBit--).toInt() and 1
        } else {
            (latitudeIndex shr latitudeBit--).toInt() and 1
        }
        if (bit == 1) {
            output[outputBit / 8] =
                (output[outputBit / 8].toInt() or (1 shl (7 - outputBit % 8))).toByte()
        }
    }
    return output
}
