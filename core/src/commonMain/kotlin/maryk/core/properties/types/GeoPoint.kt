package maryk.core.properties.types

import maryk.lib.exceptions.ParseException
import kotlin.math.roundToInt

/**
 * Geo point able to store geo coordinates up to 7 decimal/11mm accuracy
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude >= -90) { "Latitude $latitude needs to be bigger or equal to -90" }
        require(latitude <= 90) { "Latitude $latitude needs to be smaller or equal to 90" }
        require(longitude >= -180) { "Longitude $latitude needs to be smaller or equal to -180" }
        require(longitude <= 180) { "Longitude $latitude needs to be smaller or equal to 180" }
    }

    constructor(
        latitude: Int,
        longitude: Int
    ) : this(
        convertLatitude(latitude),
        convertLongitude(longitude)
    )

    /** Represent latitude as an int. Will base it on 7th decimal (11mm accuracy)*/
    fun latitudeAsInt() = (latitude * 10000000).roundToInt()
    /** Represent latitude as an int. Will base it on 7th decimal (11mm accuracy)*/
    fun longitudeAsInt() = (longitude * 10000000).roundToInt()

    /** Returns a long encoded GeoPoint */
    fun asLong() = ((this.latitudeAsInt().toULong() shl 32) + this.longitudeAsInt().toUInt()).toLong()

    override fun toString() = "$latitude,$longitude"
}

/** Convert string to geo point. Throws ParseException if invalid format. */
fun String.toGeoPoint(): GeoPoint {
    val elements = this.split(',')

    if (elements.size != 2) throw ParseException("Expected two coordinates separated by comma")

    val latitude = elements[0].toDoubleOrNull()
        ?: throw ParseException("Invalid latitude format: ${elements[0]}")
    val longitude = elements[1].trimStart().toDoubleOrNull()
        ?: throw ParseException("Invalid longitude format: ${elements[0]}")

    return GeoPoint(latitude, longitude)
}

private fun convertLatitude(latitude: Int) =
    latitude.toDouble() / 10000000

private fun convertLongitude(longitude: Int) =
    longitude.toDouble() / 10000000
