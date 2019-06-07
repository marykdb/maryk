package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationResponseType.StatsType
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.SimpleObjectValues

/** The response of the stats aggregation */
data class StatsResponse<T: Comparable<T>>(
    val reference: IsPropertyReference<out T, *, *>,
    val valueCount: ULong,
    val average: T,
    val min: T,
    val max: T,
    val sum: T
) : IsAggregationResponse {
    override val aggregationType = StatsType

    companion object : SimpleQueryDataModel<StatsResponse<*>>(
        properties = object : ObjectPropertyDefinitions<StatsResponse<*>>() {
            init {
                DefinedByReference.addReference(this, StatsResponse<*>::reference, name = "of")
                add(2u, "valueCount",
                    NumberDefinition(type = UInt64), StatsResponse<*>::valueCount)

                val contextualValueDefinition = ContextualValueDefinition(
                    contextualResolver = { context: RequestContext? ->
                        context?.reference?.let {
                            @Suppress("UNCHECKED_CAST")
                            it.comparablePropertyDefinition as IsValueDefinition<Any, IsPropertyContext>
                        } ?: throw ContextNotFoundException()
                    }
                )
                add(3u, "average", contextualValueDefinition, StatsResponse<*>::average)
                add(4u, "min", contextualValueDefinition, StatsResponse<*>::min)
                add(5u, "max", contextualValueDefinition, StatsResponse<*>::max)
                add(6u, "sum", contextualValueDefinition, StatsResponse<*>::sum)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<StatsResponse<*>>) =
            StatsResponse<Comparable<Any>>(
                reference = values(1u),
                valueCount = values(2u),
                average = values(3u),
                min = values(4u),
                max = values(5u),
                sum = values(6u)
            )
    }
}
