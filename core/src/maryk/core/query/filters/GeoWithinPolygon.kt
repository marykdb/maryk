package maryk.core.query.filters

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.BaseDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.QueryModel
import maryk.core.models.SimpleObjectModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.core.values.SimpleObjectValues

/** Inclusive point-in-polygon filter. Edges may cross the antimeridian. */
data class GeoWithinPolygon(
    val reference: AnyPropertyReference,
    val vertices: List<GeoPoint>,
) : IsFilter {
    override val filterType = FilterType.GeoWithinPolygon

    init {
        require(vertices.size >= 3) { "A polygon needs at least three vertices" }
    }

    constructor(reference: IsPropertyReference<GeoPoint, *, *>, vararg vertices: GeoPoint) :
        this(reference, vertices.toList())

    override fun singleReference(predicate: (IsPropertyReference<*, *, *>) -> Boolean) =
        reference.takeIf(predicate)

    companion object : QueryModel<GeoWithinPolygon, Companion>() {
        val reference by contextual(
            index = 1u,
            getter = GeoWithinPolygon::reference,
            definition = ContextualPropertyReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? BaseDataModel<*>? ?: throw ContextNotFoundException()
                },
            ),
        )
        val vertices by list(
            index = 2u,
            getter = GeoWithinPolygon::vertices,
            valueDefinition = EmbeddedObjectDefinition(dataModel = { GeoPolygonVertex.Model }),
            toSerializable = { GeoPolygonVertex(it.latitude, it.longitude) },
            fromSerializable = { GeoPoint(it.latitude, it.longitude) },
        )

        override fun invoke(values: ObjectValues<GeoWithinPolygon, Companion>) = GeoWithinPolygon(
            reference = values<AnyPropertyReference>(1u),
            vertices = values<List<GeoPoint>>(2u),
        )
    }
}

/** Transport representation of one [GeoWithinPolygon] vertex. */
data class GeoPolygonVertex(
    val latitude: Double,
    val longitude: Double,
) {
    internal object Model : SimpleObjectModel<GeoPolygonVertex, IsObjectDataModel<GeoPolygonVertex>>() {
        val latitude by number(1u, GeoPolygonVertex::latitude, Float64)
        val longitude by number(2u, GeoPolygonVertex::longitude, Float64)

        override fun invoke(values: SimpleObjectValues<GeoPolygonVertex>) = GeoPolygonVertex(
            latitude = values(1u),
            longitude = values(2u),
        )
    }
}
