package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.geoPoint
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.GeoPoint
import maryk.core.query.RequestContext
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanChanges
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeoFilterTest {
    @Test
    fun singleReferenceIncludesMutableParents() {
        val nested = GeoFilterParent.ref { details { location } }
        val parent = GeoFilterParent.ref { details }
        val filters = listOf(
            GeoWithinBox(nested, -1.0, -1.0, 1.0, 1.0),
            GeoWithinRadius(nested, GeoPoint(0.0, 0.0), 1000.0),
            GeoWithinPolygon(nested, GeoPoint(-1.0, -1.0), GeoPoint(-1.0, 1.0), GeoPoint(1.0, 0.0)),
        )
        for (filter in filters) {
            assertEquals(nested, filter.singleReference { it == nested })
            assertEquals(nested, filter.singleReference { it == parent })
            assertNull(filter.singleReference { it == reference })
            assertFailsWith<RequestException> { GeoFilterParent.scanChanges(where = filter) }
        }
    }

    private val reference = CompleteMarykModel.location.ref()
    private val context = RequestContext(mapOf(
        CompleteMarykModel.Meta.name to DataModelReference(CompleteMarykModel)
    ))

    @Test
    fun matchesBoxAndRadiusExactly() {
        val box = GeoWithinBox(reference, 52.0, 5.0, 52.2, 5.2)
        val radius = GeoWithinRadius(reference, GeoPoint(52.0907, 5.1214), 100.0)

        assertTrue(matchesFilter(box, valueMatcher = { _, matcher -> matcher(GeoPoint(52.1, 5.1)) }))
        assertFalse(matchesFilter(box, valueMatcher = { _, matcher -> matcher(GeoPoint(53.0, 5.1)) }))
        assertTrue(matchesFilter(radius, valueMatcher = { _, matcher -> matcher(GeoPoint(52.0907, 5.1214)) }))
        assertFalse(matchesFilter(radius, valueMatcher = { _, matcher -> matcher(GeoPoint(53.0, 5.1214)) }))
        assertFailsWith<IllegalArgumentException> {
            GeoWithinRadius(reference, GeoPoint(0.0, 0.0), -1.0)
        }
    }

    @Test
    fun matchesPolygonExactly() {
        val polygon = GeoWithinPolygon(
            reference,
            listOf(
                GeoPoint(0.0, 170.0),
                GeoPoint(0.0, -170.0),
                GeoPoint(20.0, -170.0),
                GeoPoint(20.0, 170.0),
            ),
        )

        assertTrue(matchesFilter(polygon, valueMatcher = { _, matcher -> matcher(GeoPoint(10.0, 179.5)) }))
        assertFalse(matchesFilter(polygon, valueMatcher = { _, matcher -> matcher(GeoPoint(10.0, 0.0)) }))
        assertFailsWith<IllegalArgumentException> {
            GeoWithinPolygon(reference, listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)))
        }
    }

    @Test
    fun roundTripsWithRequests() {
        val filters = listOf(
            GeoWithinBox(reference, 52.0, 5.0, 52.2, 5.2),
            GeoWithinRadius(reference, GeoPoint(52.0907, 5.1214), 100.0),
            GeoWithinPolygon(
                reference,
                listOf(
                    GeoPoint(52.0, 5.0),
                    GeoPoint(52.0, 5.2),
                    GeoPoint(52.2, 5.2),
                ),
            ),
        )
        filters.forEach { filter ->
            val request = CompleteMarykModel.scan(where = filter, allowTableScan = true)
            checkProtoBufConversion(request, ScanRequest, { context })
            checkJsonConversion(request, ScanRequest, { context })
            checkYamlConversion(request, ScanRequest, { context })
        }
    }
}

private object GeoFilterLeaf : DataModel<GeoFilterLeaf>() {
    val location by geoPoint(1u, required = true, final = true)
}

private object GeoFilterParent : RootDataModel<GeoFilterParent>() {
    val details by embed(1u, required = false, dataModel = { GeoFilterLeaf })
}
