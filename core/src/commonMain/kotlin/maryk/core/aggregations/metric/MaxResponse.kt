package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationResponseType.MaxType
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

/** The response of the find maximum aggregation */
data class MaxResponse<T: Comparable<T>>(
    val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>,
    val value: T?
) : IsAggregationResponse {
    override val aggregationType = MaxType

    @Suppress("unused")
    companion object : SimpleQueryModel<MaxResponse<*>>() {
        val of by addReference(MaxResponse<*>::reference)

        val value by contextual(
            index = 2u,
            getter = MaxResponse<*>::value,
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

        override fun invoke(values: SimpleObjectValues<MaxResponse<*>>) =
            MaxResponse<Comparable<Any>>(
                reference = values(1u),
                value = values(2u)
            )
    }
}
