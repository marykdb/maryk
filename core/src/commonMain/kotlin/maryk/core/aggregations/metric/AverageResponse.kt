package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationResponseType.AverageType
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
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

/** The response of the find average value aggregation */
data class AverageResponse<T: Comparable<T>>(
    val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>,
    val value: T?,
    val valueCount: ULong // The value count is included so multiple responses of sharded tables can be joined
) : IsAggregationResponse {
    override val aggregationType = AverageType

    @Suppress("unused")
    companion object : SimpleQueryDataModel<AverageResponse<*>>(
        properties = object : ObjectPropertyDefinitions<AverageResponse<*>>() {
            val of by addReference(AverageResponse<*>::reference)
            val value by contextual(
                index = 2u,
                getter = AverageResponse<*>::value,
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
            val valueCount by number(3u, AverageResponse<*>::valueCount, type = UInt64)
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
