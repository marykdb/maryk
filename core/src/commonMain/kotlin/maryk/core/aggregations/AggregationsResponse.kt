package maryk.core.aggregations

import maryk.core.models.SingleValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.StringDefinition
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

    internal object Properties : ObjectPropertyDefinitions<AggregationsResponse>() {
        val namedAggregations = add(
            1u, "namedAggregations",
            MapDefinition(
                keyDefinition = StringDefinition(),
                valueDefinition = MultiTypeDefinition(
                    typeEnum = AggregationResponseType
                )
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
            },
            getter = AggregationsResponse::namedAggregations
        )
    }

    internal companion object : SingleValueDataModel<Map<String, TypedValue<AggregationResponseType, IsAggregationResponse>>, Map<String, IsAggregationResponse>, AggregationsResponse, Properties, RequestContext>(
        properties = Properties,
        singlePropertyDefinition = Properties.namedAggregations
    ) {
        override fun invoke(values: ObjectValues<AggregationsResponse, Properties>) = AggregationsResponse(
            namedAggregations = values(1u)
        )
    }
}
