package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationResponseType.MinType
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.SimpleObjectValues

/** The response of the find minimum aggregation */
data class MinResponse<T: Comparable<T>>(
    val reference: IsPropertyReference<out T, *, *>,
    val value: T?
) : IsAggregationResponse {
    override val aggregationType = MinType

    companion object : SimpleQueryDataModel<MinResponse<*>>(
        properties = object : ObjectPropertyDefinitions<MinResponse<*>>() {
            init {
                DefinedByReference.addReference(this, MinResponse<*>::reference, name = "of")
                add(2u, "value",
                    ContextualValueDefinition(
                        required = false,
                        contextualResolver = { context: RequestContext? ->
                            context?.reference?.let {
                                @Suppress("UNCHECKED_CAST")
                                it.comparablePropertyDefinition as IsValueDefinition<Any, IsPropertyContext>
                            } ?: throw ContextNotFoundException()
                        }
                    ),
                    MinResponse<*>::value
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<MinResponse<*>>) =
            MinResponse<Comparable<Any>>(
                reference = values(1u),
                value = values(2u)
            )
    }
}
