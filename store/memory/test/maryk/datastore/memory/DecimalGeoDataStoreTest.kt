package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.decimal
import maryk.core.properties.definitions.geoPoint
import maryk.core.properties.definitions.index.GeoHash
import maryk.core.properties.types.Decimal
import maryk.core.properties.types.GeoPoint
import maryk.core.query.ValueRange
import maryk.core.query.filters.And
import maryk.core.query.filters.GeoWithinBox
import maryk.core.query.filters.GeoWithinPolygon
import maryk.core.query.filters.GeoWithinRadius
import maryk.core.query.filters.Range
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DecimalGeoDataStoreTest {
    object AmountModel : RootDataModel<AmountModel>(
        indexes = { listOf(AmountModel.amount.ref()) }
    ) {
        val amount by decimal(index = 1u, scale = 2u)
    }

    object LocationModel : RootDataModel<LocationModel>(
        indexes = { listOf(GeoHash(LocationModel.location.ref(), 32u)) }
    ) {
        val location by geoPoint(index = 1u)
    }

    @Test
    fun decimalRangeUsesExactIndexOrder() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to AmountModel))
        try {
            listOf("10.05", "2.10", "10.50").forEach { amount ->
                store.execute(
                    AmountModel.add(AmountModel.create {
                        this.amount with Decimal.parse(amount)
                    })
                )
            }

            val response = store.execute(
                AmountModel.scan(
                    where = Range(
                        AmountModel.amount.ref() with ValueRange(
                            Decimal.parse("10.00"),
                            Decimal.parse("10.50"),
                        )
                    ),
                    order = AmountModel.amount.ref().ascending(),
                )
            )

            assertIs<FetchByIndexScan>(response.dataFetchType)
            assertEquals(
                listOf("10.05", "10.50"),
                response.values.map { it.values { amount }.toString() },
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun spatialFiltersUseGeohashCandidatesAndExactPostFilter() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to LocationModel))
        try {
            listOf(
                GeoPoint(52.0907, 5.1214),
                GeoPoint(52.3676, 4.9041),
                GeoPoint(10.0, 179.5),
                GeoPoint(10.0, -179.5),
            ).forEach { point ->
                store.execute(
                    LocationModel.add(LocationModel.create {
                        location with point
                    })
                )
            }

            val radius = store.execute(
                LocationModel.scan(
                    where = GeoWithinRadius(
                        LocationModel.location.ref(),
                        GeoPoint(52.0907, 5.1214),
                        40_000.0,
                    )
                )
            )
            assertIs<FetchByIndexScan>(radius.dataFetchType)
            assertEquals(2, radius.values.size)

            val antimeridian = store.execute(
                LocationModel.scan(
                    where = GeoWithinBox(
                        LocationModel.location.ref(),
                        0.0,
                        170.0,
                        20.0,
                        -170.0,
                    )
                )
            )
            assertIs<FetchByIndexScan>(antimeridian.dataFetchType)
            assertEquals(2, antimeridian.values.size)

            val polygon = store.execute(
                LocationModel.scan(
                    where = GeoWithinPolygon(
                        LocationModel.location.ref(),
                        listOf(
                            GeoPoint(0.0, 170.0),
                            GeoPoint(0.0, -170.0),
                            GeoPoint(20.0, -170.0),
                            GeoPoint(20.0, 170.0),
                        ),
                    ),
                ),
            )
            assertIs<FetchByIndexScan>(polygon.dataFetchType)
            assertEquals(2, polygon.values.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun combinesSpatialFiltersOnTheSameGeohashIndex() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to LocationModel))
        try {
            listOf(
                GeoPoint(52.0907, 5.1214),
                GeoPoint(52.3676, 4.9041),
            ).forEach { point ->
                store.execute(LocationModel.add(LocationModel.create { location with point }))
            }

            val response = store.execute(
                LocationModel.scan(
                    where = And(
                        GeoWithinBox(LocationModel.location.ref(), 52.0, 5.0, 52.2, 5.2),
                        GeoWithinRadius(LocationModel.location.ref(), GeoPoint(52.0907, 5.1214), 40_000.0),
                    ),
                ),
            )

            assertIs<FetchByIndexScan>(response.dataFetchType)
            assertEquals(1, response.values.size)
        } finally {
            store.close()
        }
    }
}
