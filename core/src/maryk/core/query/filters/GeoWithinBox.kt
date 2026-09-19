package maryk.core.query.filters

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.BaseDataModel
import maryk.core.models.QueryModel
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.geoPoint
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.GeoPoint
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/** Inclusive geographic bounding-box filter. West above east crosses the antimeridian. */
data class GeoWithinBox(
    val reference: AnyPropertyReference,
    val southWest: GeoPoint,
    val northEast: GeoPoint,
) : IsFilter {
    override val filterType = FilterType.GeoWithinBox

    init {
        require(southWest.latitude <= northEast.latitude) {
            "South latitude must not exceed north latitude"
        }
    }

    constructor(
        reference: IsPropertyReference<GeoPoint, *, *>,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ) : this(reference, GeoPoint(south, west), GeoPoint(north, east))

    override fun singleReference(predicate: (IsPropertyReference<*, *, *>) -> Boolean) =
        reference.takeIf(predicate)

    companion object : QueryModel<GeoWithinBox, Companion>() {
        val reference by contextual(
            index = 1u,
            getter = GeoWithinBox::reference,
            definition = ContextualPropertyReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? BaseDataModel<*>? ?: throw ContextNotFoundException()
                }
            )
        )
        val southWest by geoPoint(2u, GeoWithinBox::southWest)
        val northEast by geoPoint(3u, GeoWithinBox::northEast)

        override fun invoke(values: ObjectValues<GeoWithinBox, Companion>) = GeoWithinBox(
            reference = values(1u),
            southWest = values(2u),
            northEast = values(3u),
        )
    }
}
