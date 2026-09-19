package maryk.core.properties.types

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeoGeometryTest {
    @Test
    fun validatesAndMeasuresPoints() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(0.0, Double.POSITIVE_INFINITY) }

        val utrecht = GeoPoint(52.0907, 5.1214)
        val amsterdam = GeoPoint(52.3676, 4.9041)
        assertEquals(34_600.0, utrecht.distanceTo(amsterdam), 500.0)
        assertEquals(0.0, utrecht.distanceTo(utrecht))
    }

    @Test
    fun matchesInclusiveBoxesIncludingAntimeridian() {
        assertTrue(GeoPoint(10.0, 179.5).isWithinBox(0.0, 170.0, 20.0, -170.0))
        assertTrue(GeoPoint(10.0, -179.5).isWithinBox(0.0, 170.0, 20.0, -170.0))
        assertFalse(GeoPoint(10.0, 0.0).isWithinBox(0.0, 170.0, 20.0, -170.0))
        assertTrue(GeoPoint(20.0, -170.0).isWithinBox(0.0, 170.0, 20.0, -170.0))
        assertFailsWith<IllegalArgumentException> {
            GeoPoint(0.0, 0.0).isWithinBox(10.0, -10.0, 0.0, 10.0)
        }
    }

    @Test
    fun matchesInclusivePolygonsIncludingAntimeridian() {
        val polygon = listOf(
            GeoPoint(0.0, 170.0),
            GeoPoint(0.0, -170.0),
            GeoPoint(20.0, -170.0),
            GeoPoint(20.0, 170.0),
        )

        assertTrue(GeoPoint(10.0, 179.5).isWithinPolygon(polygon))
        assertTrue(GeoPoint(10.0, -179.5).isWithinPolygon(polygon))
        assertTrue(GeoPoint(0.0, 175.0).isWithinPolygon(polygon))
        assertFalse(GeoPoint(10.0, 0.0).isWithinPolygon(polygon))
        assertFailsWith<IllegalArgumentException> {
            GeoPoint(0.0, 0.0).isWithinPolygon(listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)))
        }
    }

    @Test
    fun createsStableGeohashPrefixes() {
        val point = GeoPoint(52.0907, 5.1214)
        assertContentEquals(point.geoHashBits(32u), point.geoHashBits(32u))
        assertEquals(4, point.geoHashBits(32u).size)
        assertTrue(point.geoHashBits(16u).contentEquals(point.geoHashBits(32u).copyOf(2)))
        assertFailsWith<IllegalArgumentException> { point.geoHashBits(0u) }
        assertFailsWith<IllegalArgumentException> { point.geoHashBits(53u) }
    }
}
