package maryk.core.aggregations

import maryk.core.properties.SingleValueModel
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/** For multiple aggregations responses named by a String */
data class AggregationsResponse internal constructor(
    val namedAggregations: Map<String, IsAggregationResponse>
) {
    constructor(
        vararg aggregationPair: Pair<String, IsAggregationResponse>
    ) : this(aggregationPair.toMap())

    companion object : SingleValueModel<Map<String, TypedValue<AggregationResponseType, IsAggregationResponse>>, Map<String, IsAggregationResponse>, AggregationsResponse, Companion, RequestContext>(
        singlePropertyDefinitionGetter = { Companion.namedAggregations }
    ) {
        val namedAggregations by map(
            index = 1u,
            getter = AggregationsResponse::namedAggregations,
            keyDefinition = StringDefinition(),
            valueDefinition = MultiTypeDefinition(
                typeEnum = AggregationResponseType
            ),
            toSerializable = { value, _ ->
                value?.mapValues { (_, value) ->
                    TypedValue(value.aggregationType, value)
                }
            },
            fromSerializable = { values: Map<String, TypedValue<AggregationResponseType, IsAggregationResponse>>? ->
                values?.mapValues { (_, value) ->
                    value.value
                }
            }
        )

        override fun invoke(values: ObjectValues<AggregationsResponse, Companion>) = AggregationsResponse(
            namedAggregations = values(1u)
        )
    }
}
