package maryk.core.properties.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeoPointTest {
    @Test
    fun initTest() {
        GeoPoint(56.2345, 140.233214)
        GeoPoint(-23.532, -90.18231)

        assertFailsWith<IllegalArgumentException> {
            GeoPoint(-96.24242, -90.18231)
        }

        assertFailsWith<IllegalArgumentException> {
            GeoPoint(106.35938, 110.3242)
        }

        assertFailsWith<IllegalArgumentException> {
            GeoPoint(42.12423, 190.3242)
        }

        assertFailsWith<IllegalArgumentException> {
            GeoPoint(42.12423, -190.3242)
        }
    }

    @Test
    fun convertCoordinates() {
        val point = GeoPoint(56.2345, 140.233214)
        val lat = point.latitudeAsInt()
        val long = point.longitudeAsInt()
        val converted = GeoPoint(lat, long)

        assertEquals(point, converted)
    }

    @Test
    fun convertToString() {
        assertEquals("52.0906448,5.1212607", GeoPoint(52.0906448, 5.1212607).toString())
        assertEquals("-54.8034246,-68.2969808", GeoPoint(-54.8034246, -68.2969808).toString())
    }

    @Test
    fun parseFromString() {
        assertEquals("52.0906448,5.1212607".toGeoPoint(), GeoPoint(52.0906448, 5.1212607))
        assertEquals("-54.8034246, -68.2969808".toGeoPoint(), GeoPoint(-54.8034246, -68.2969808))
    }
}
