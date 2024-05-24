package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationResponseType.StatsType
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** The response of the stats aggregation */
data class StatsResponse<T: Comparable<T>>(
    val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>,
    val valueCount: ULong,
    val average: T?,
    val min: T?,
    val max: T?,
    var sum: T?
) : IsAggregationResponse {
    override val aggregationType = StatsType

    companion object : SimpleQueryModel<StatsResponse<*>>() {
        val of by addReference(StatsResponse<*>::reference)

        val valueCount by number(2u, StatsResponse<*>::valueCount, UInt64)

        private val contextualValueDefinition = ContextualValueDefinition(
            required = false,
            contextualResolver = { context: RequestContext? ->
                context?.reference?.let {
                    @Suppress("UNCHECKED_CAST")
                    it.comparablePropertyDefinition as IsValueDefinition<Any, IsPropertyContext>
                } ?: throw ContextNotFoundException()
            }
        )
        val average by contextual(
            index = 3u,
            getter = StatsResponse<*>::average,
            definition = contextualValueDefinition
        )
        val min by contextual(
            index = 4u,
            getter = StatsResponse<*>::min,
            definition = contextualValueDefinition
        )
        val max by contextual(
            index = 5u,
            getter = StatsResponse<*>::max,
            definition = contextualValueDefinition
        )
        val sum by contextual(
            index = 6u,
            getter = { it.sum },
            definition = contextualValueDefinition
        )

        override fun invoke(values: SimpleObjectValues<StatsResponse<*>>) =
            StatsResponse<Comparable<Any>>(
                reference = values(of.index),
                valueCount = values(valueCount.index),
                average = values(average.index),
                min = values(min.index),
                max = values(max.index),
                sum = values(sum.index)
            )
    }
}
