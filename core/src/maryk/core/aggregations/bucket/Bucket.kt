package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationsResponse
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext
import maryk.core.values.SimpleObjectValues

/** Bucket collecting aggregation results */
data class Bucket<out T: Any>(
    val key: T,
    val aggregations: AggregationsResponse,
    val count: ULong
) {
    companion object : SimpleQueryModel<Bucket<*>>() {
        val key by contextual(
            index = 1u,
            getter = Bucket<*>::key,
            definition = ContextualValueDefinition(
                contextualResolver = { context: RequestContext? ->
                    context?.reference?.let {
                        @Suppress("UNCHECKED_CAST")
                        it.comparablePropertyDefinition as IsValueDefinition<Any, IsPropertyContext>
                    } ?: throw ContextNotFoundException()
                }
            )
        )
        val aggregations by contextual(
            2u,
            definition = ContextTransformerDefinition(
                EmbeddedObjectDefinition(dataModel = { AggregationsResponse })
            ) { context: RequestContext? -> context?.let { RequestContext(context.definitionsContext, context.dataModel) } },
            getter = Bucket<*>::aggregations,
            alternativeNames = setOf("aggs")
        )
        val count by number(3u, Bucket<*>::count, type = UInt64)

        override fun invoke(values: SimpleObjectValues<Bucket<*>>) = Bucket(
            key = values(key.index),
            aggregations = values(aggregations.index),
            count = values(count.index)
        )
    }
}
