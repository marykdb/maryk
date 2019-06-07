package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationResponseType.MaxType
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

/** The response of the find maximum aggregation */
data class MaxResponse<T: Comparable<T>>(
    val reference: IsPropertyReference<out T, *, *>,
    val value: T
) : IsAggregationResponse {
    override val aggregationType = MaxType

    companion object : SimpleQueryDataModel<MaxResponse<*>>(
        properties = object : ObjectPropertyDefinitions<MaxResponse<*>>() {
            init {
                DefinedByReference.addReference(this, MaxResponse<*>::reference, name = "of")
                add(2u, "value",
                    ContextualValueDefinition(
                        contextualResolver = { context: RequestContext? ->
                            context?.reference?.let {
                                @Suppress("UNCHECKED_CAST")
                                it.comparablePropertyDefinition as IsValueDefinition<Any, IsPropertyContext>
                            } ?: throw ContextNotFoundException()
                        }
                    ),
                    MaxResponse<*>::value
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<MaxResponse<*>>) =
            MaxResponse<Comparable<Any>>(
                reference = values(1u),
                value = values(2u)
            )
    }
}
