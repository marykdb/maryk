package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationResponseType.SumType
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

/** The response of the sum aggregation */
data class SumResponse<T: Any>(
    val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>,
    val value: T?
) : IsAggregationResponse {
    override val aggregationType = SumType

    companion object : SimpleQueryModel<SumResponse<*>>() {
        val of by addReference(SumResponse<*>::reference)

        val value by contextual(
            index = 2u,
            getter = SumResponse<*>::value,
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

        override fun invoke(values: SimpleObjectValues<SumResponse<*>>) =
            SumResponse<Comparable<Any>>(
                reference = values(1u),
                value = values(2u)
            )
    }
}
