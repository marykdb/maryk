package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationResponseType.MinType
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** The response of the find minimum aggregation */
data class MinResponse<T: Comparable<T>>(
    val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>,
    val value: T?
) : IsAggregationResponse {
    override val aggregationType = MinType

    companion object : SimpleQueryModel<MinResponse<*>>() {
        val of by addReference(MinResponse<*>::reference)

        val value by contextual(
            index = 2u,
            getter = MinResponse<*>::value,
            definition = ContextualValueDefinition(
                required = false,
                contextualResolver = { context: RequestContext? ->
                    context?.reference?.let {
                        @Suppress("UNCHECKED_CAST")
                        it.comparablePropertyDefinition as IsValueDefinition<Any, IsPropertyContext>
                    } ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: SimpleObjectValues<MinResponse<*>>) =
            MinResponse<Comparable<Any>>(
                reference = values(1u),
                value = values(2u)
            )
    }
}
