package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationRequestType.DateHistogramType
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
data class DateHistogram<T: IsTemporal<*>>(
    override val reference: IsPropertyReference<out T, *, *>,
    val dateUnit: DateUnit,
    val aggregations: Aggregations? = null
) : IsAggregationRequest<T, IsPropertyReference<out T, *, *>, DateHistogramResponse<T>>,
    DefinedByReference<IsTemporal<*>> {
    override val aggregationType = DateHistogramType

    override fun createAggregator() = DateHistogramAggregator(this)

    companion object : SimpleQueryDataModel<DateHistogram<*>>(
        properties = object : ObjectPropertyDefinitions<DateHistogram<*>>() {
            init {
                DefinedByReference.addReference(this, DateHistogram<*>::reference, name = "of")
                add(
                    3u, "dateUnit", EnumDefinition(enum = DateUnit), DateHistogram<*>::dateUnit
                )
                IsAggregationRequest.addAggregationsDefinition(this, DateHistogram<*>::aggregations)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<DateHistogram<*>>) = DateHistogram<IsTemporal<Any>>(
            reference = values(1u),
            aggregations = values(2u),
            dateUnit = values(3u)
        )
    }
}
