package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationResponseType.DateHistogramType
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** The [buckets] found for all dates separated by unit at [reference] */
data class DateHistogramResponse<T: Comparable<*>>(
    val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>,
    val buckets: List<Bucket<T>> = emptyList()
) : IsAggregationResponse {
    override val aggregationType = DateHistogramType

    @Suppress("unused")
    companion object : SimpleQueryModel<DateHistogramResponse<*>>() {
        val of by addReference(DateHistogramResponse<*>::reference)
        val buckets by list(
            index = 2u,
            getter = DateHistogramResponse<*>::buckets,
            default = emptyList(),
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { Bucket.Model }
            )
        )

        override fun invoke(values: SimpleObjectValues<DateHistogramResponse<*>>) =
            DateHistogramResponse<Comparable<Any>>(
                reference = values(1u),
                buckets = values(2u)
            )
    }
}
