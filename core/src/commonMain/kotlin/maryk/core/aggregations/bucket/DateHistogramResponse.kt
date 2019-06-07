package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationResponseType.DateHistogramType
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues
import maryk.lib.time.IsTemporal

/** The [buckets] found for all dates separated by unit at [reference] */
data class DateHistogramResponse<T: IsTemporal<*>>(
    val reference: IsPropertyReference<out T, *, *>,
    val buckets: List<Bucket<T>> = emptyList()
) : IsAggregationResponse {
    override val aggregationType = DateHistogramType

    companion object : SimpleQueryDataModel<DateHistogramResponse<*>>(
        properties = object : ObjectPropertyDefinitions<DateHistogramResponse<*>>() {
            init {
                DefinedByReference.addReference(this, DateHistogramResponse<*>::reference, name = "of")
                add(2u, "buckets",
                    ListDefinition(
                        default = emptyList(),
                        valueDefinition = EmbeddedObjectDefinition(
                            dataModel = { Bucket }
                        )
                    ),
                    DateHistogramResponse<*>::buckets
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<DateHistogramResponse<*>>) =
            DateHistogramResponse<IsTemporal<Any>>(
                reference = values(1u),
                buckets = values(2u)
            )
    }
}
