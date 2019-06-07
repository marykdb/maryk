package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext
import maryk.core.values.SimpleObjectValues

/** Bucket collecting aggregation results */
data class Bucket<out T: Any>(
    val key: T,
    val aggregations: AggregationsResponse,
    val count: ULong
) {
    companion object : SimpleQueryDataModel<Bucket<*>>(
        properties = object : ObjectPropertyDefinitions<Bucket<*>>() {
            init {
                add(1u, "key",
                    ContextualValueDefinition(
                        contextualResolver = { context: RequestContext? ->
                            context?.reference?.let {
                                @Suppress("UNCHECKED_CAST")
                                it.comparablePropertyDefinition as IsValueDefinition<Any, IsPropertyContext>
                            } ?: throw ContextNotFoundException()
                        }
                    ),
                    Bucket<*>::key
                )
                IsAggregationResponse.addAggregationsDefinition(this, Bucket<*>::aggregations)
                add(3u, "count", NumberDefinition(type = UInt64), Bucket<*>::count)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<Bucket<*>>) = Bucket(
            key = values(1u),
            aggregations = values(2u),
            count = values(3u)
        )
    }
}
