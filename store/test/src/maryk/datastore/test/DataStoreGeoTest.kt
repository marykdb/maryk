package maryk.datastore.test

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.geoPoint
import maryk.core.properties.definitions.index.GeoHash
import maryk.core.properties.definitions.string
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.Key
import maryk.core.query.filters.And
import maryk.core.query.filters.GeoWithinBox
import maryk.core.query.filters.GeoWithinPolygon
import maryk.core.query.filters.GeoWithinRadius
import maryk.core.query.filters.IsFilter
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import kotlin.test.assertEquals
import kotlin.test.assertIs

object GeoLocation : RootDataModel<GeoLocation>(
    indexes = { listOf(GeoHash(GeoLocation.location.ref(), 32u)) },
) {
    val name by string(index = 1u)
    val location by geoPoint(index = 2u)
}

class DataStoreGeoTest(
    private val dataStore: IsDataStore,
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<GeoLocation>>()

    override val allTests = mapOf(
        "executesGeoRadiusIndexScan" to ::executesGeoRadiusIndexScan,
        "executesGeoBoxIndexScanAcrossAntimeridian" to ::executesGeoBoxIndexScanAcrossAntimeridian,
        "executesGeoPolygonIndexScanAcrossAntimeridian" to ::executesGeoPolygonIndexScanAcrossAntimeridian,
        "combinesGeoFiltersOnOneIndex" to ::combinesGeoFiltersOnOneIndex,
    )

    override suspend fun initData() {
        listOf(
            "Utrecht" to GeoPoint(52.0907, 5.1214),
            "Amsterdam" to GeoPoint(52.3676, 4.9041),
            "Dateline east" to GeoPoint(10.0, 179.5),
            "Dateline west" to GeoPoint(10.0, -179.5),
            "New York" to GeoPoint(40.7128, -74.0060),
        ).forEach { (name, location) ->
            val status = dataStore.execute(
                GeoLocation.add(GeoLocation.create {
                    this.name with name
                    this.location with location
                }),
            ).statuses.single()
            keys += assertStatusIs<AddSuccess<GeoLocation>>(status).key
        }
    }

    override suspend fun resetData() {
        dataStore.execute(GeoLocation.delete(*keys.toTypedArray(), hardDelete = true)).statuses.forEach { status ->
            assertStatusIs<DeleteSuccess<GeoLocation>>(status)
        }
        keys.clear()
    }

    private suspend fun executesGeoRadiusIndexScan() {
        assertMatches(
            GeoWithinRadius(GeoLocation.location.ref(), GeoPoint(52.0907, 5.1214), 40_000.0),
            "Utrecht",
            "Amsterdam",
        )
    }

    private suspend fun executesGeoBoxIndexScanAcrossAntimeridian() {
        assertMatches(
            GeoWithinBox(GeoLocation.location.ref(), 0.0, 170.0, 20.0, -170.0),
            "Dateline east",
            "Dateline west",
        )
    }

    private suspend fun executesGeoPolygonIndexScanAcrossAntimeridian() {
        assertMatches(
            GeoWithinPolygon(
                GeoLocation.location.ref(),
                GeoPoint(0.0, 170.0),
                GeoPoint(0.0, -170.0),
                GeoPoint(20.0, -170.0),
                GeoPoint(20.0, 170.0),
            ),
            "Dateline east",
            "Dateline west",
        )
    }

    private suspend fun combinesGeoFiltersOnOneIndex() {
        assertMatches(
            And(
                GeoWithinBox(GeoLocation.location.ref(), 52.0, 5.0, 52.2, 5.2),
                GeoWithinRadius(GeoLocation.location.ref(), GeoPoint(52.0907, 5.1214), 40_000.0),
            ),
            "Utrecht",
        )
    }

    private suspend fun assertMatches(filter: IsFilter, vararg names: String) {
        val response = dataStore.execute(GeoLocation.scan(where = filter))
        assertIs<FetchByIndexScan>(response.dataFetchType)
        assertEquals(names.toSet(), response.values.map { it.values { name } }.toSet())
    }
}
