package maryk.core.properties.definitions.index

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.geoPoint
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.GeoBounds
import maryk.core.properties.types.geoHashBits
import maryk.core.query.DefinitionsConversionContext
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.query.filters.GeoWithinRadius
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeoHashTest {
    object GeoModel : RootDataModel<GeoModel>(
        indexes = { listOf(GeoHash(GeoModel.location.ref(), 32u)) }
    ) {
        val location by geoPoint(index = 1u, final = true)
    }

    private val context = DefinitionsConversionContext(propertyDefinitions = GeoModel)

    @Test
    fun emitsStableFixedSizePrefix() {
        val point = GeoPoint(52.0907, 5.1214)
        val values = GeoModel.create { location with point }
        val index = GeoModel.Meta.indexes!!.single() as GeoHash

        assertContentEquals(point.geoHashBits(32u), index.toStorageByteArrays(values).single())
        assertEquals(4, index.toStorageByteArrays(values).single().size)
        assertFailsWith<IllegalArgumentException> { GeoHash(GeoModel.location.ref(), 0u) }
    }

    @Test
    fun roundTripsDefinition() {
        val index = GeoHash(GeoModel.location.ref(), 27u)
        checkProtoBufConversion(index, GeoHash.Model, { context })
        checkJsonConversion(index, GeoHash.Model, { context })
        checkYamlConversion(index, GeoHash.Model, { context })
    }

    @Test
    fun boundedCoverageContainsCandidatePoint() {
        val index = GeoHash(GeoModel.location.ref(), 32u)
        val point = GeoPoint(52.0907, 5.1214)
        val prefixes = index.coveringPrefixes(GeoBounds(52.0, 5.0, 52.2, 5.2))

        assertTrue(prefixes.size <= MAX_GEOHASH_COVER_CELLS)
        assertTrue(prefixes.any { prefix ->
            point.geoHashBits(32u).copyOf(prefix.size).contentEquals(prefix)
        })
    }

    @Test
    fun radiusPlannerMatchesStoredIndexEntry() {
        val point = GeoPoint(52.0907, 5.1214)
        val values = GeoModel.create { location with point }
        val index = GeoModel.Meta.indexes!!.single() as GeoHash
        val keyRanges = GeoModel.createScanRange(null, null)
        val ranges = index.createScanRange(
            GeoWithinRadius(GeoModel.location.ref(), point, 40_000.0),
            keyRanges,
        )
        val indexValue = index.toStorageByteArrayForIndex(values, ByteArray(16))!!

        assertTrue(ranges.ranges.isNotEmpty())
        assertTrue(
            ranges.matchesPartials(indexValue, length = index.byteSize),
            "Stored geohash must match one planned candidate prefix",
        )
    }
}
