package maryk.core.query.filters

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.BaseDataModel
import maryk.core.models.QueryModel
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.geoPoint
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/** Inclusive great-circle radius filter measured in metres. */
data class GeoWithinRadius(
    val reference: AnyPropertyReference,
    val center: GeoPoint,
    val radiusMeters: Double,
) : IsFilter {
    override val filterType = FilterType.GeoWithinRadius

    init {
        require(radiusMeters.isFinite() && radiusMeters >= 0.0) {
            "Radius must be a finite, non-negative number of metres"
        }
    }

    override fun singleReference(predicate: (IsPropertyReference<*, *, *>) -> Boolean) =
        reference.takeIf(predicate)

    companion object : QueryModel<GeoWithinRadius, Companion>() {
        val reference by contextual(
            index = 1u,
            getter = GeoWithinRadius::reference,
            definition = ContextualPropertyReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? BaseDataModel<*>? ?: throw ContextNotFoundException()
                }
            )
        )
        val center by geoPoint(2u, GeoWithinRadius::center)
        val radiusMeters by number(3u, GeoWithinRadius::radiusMeters, Float64, minValue = 0.0)

        override fun invoke(values: ObjectValues<GeoWithinRadius, Companion>) = GeoWithinRadius(
            reference = values(1u),
            center = values(2u),
            radiusMeters = values(3u),
        )
    }
}
