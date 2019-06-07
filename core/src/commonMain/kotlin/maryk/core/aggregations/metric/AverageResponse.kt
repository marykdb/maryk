package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationResponseType.AverageType
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

/** The response of the find maximum aggregation */
data class AverageResponse<T: Comparable<T>>(
    val reference: IsPropertyReference<out T, *, *>,
    val value: T,
    val valueCount: ULong // The value count is included so multiple responses of sharded tables can be joined
) : IsAggregationResponse {
    override val aggregationType = AverageType

    companion object : SimpleQueryDataModel<AverageResponse<*>>(
        properties = object : ObjectPropertyDefinitions<AverageResponse<*>>() {
            init {
                DefinedByReference.addReference(this, AverageResponse<*>::reference, name = "of")
                add(2u, "value",
                    ContextualValueDefinition(
                        contextualResolver = { context: RequestContext? ->
                            context?.reference?.let {
                                @Suppress("UNCHECKED_CAST")
                                it.comparablePropertyDefinition as IsValueDefinition<Any, IsPropertyContext>
                            } ?: throw ContextNotFoundException()
                        }
                    ),
                    AverageResponse<*>::value
                )

                add(3u, "valueCount", NumberDefinition(type = UInt64), AverageResponse<*>::valueCount)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<AverageResponse<*>>) =
            AverageResponse<Comparable<Any>>(
                reference = values(1u),
                value = values(2u),
                valueCount = values(3u)
            )
    }
}
