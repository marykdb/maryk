package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationType.DateHistogramType
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues
import maryk.lib.time.IsTemporal

/** Bucket all together that are on same date/time for [reference] */
data class DateHistogram(
    override val reference: IsPropertyReference<out IsTemporal<*>, *, *>,
    val dateUnit: DateHistogramUnit,
    val aggregations: Aggregations? = null
) : IsAggregationRequest, DefinedByReference<IsTemporal<*>> {
    override val aggregationType = DateHistogramType

    companion object : SimpleQueryDataModel<DateHistogram>(
        properties = object : ObjectPropertyDefinitions<DateHistogram>() {
            init {
                DefinedByReference.addReference(this, DateHistogram::reference, name = "of")
                add(
                    3u, "dateUnit", EnumDefinition(enum = DateHistogramUnit), DateHistogram::dateUnit
                )
                IsAggregationRequest.addAggregationsDefinition(this, DateHistogram::aggregations)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<DateHistogram>) = DateHistogram(
            reference = values(1u),
            aggregations = values(2u),
            dateUnit = values(3u)
        )
    }
}
