package maryk.core.aggregations

import maryk.core.models.SingleValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/**
 * For defining aggregations to run on Scan or Get.
 * They are each defined as a pair with a String and the aggregation definition. This way the output can be referred to.
 */
data class Aggregations internal constructor(
    val namedAggregations: Map<String, IsAggregationRequest<*, *>>
) {
    constructor(
        vararg aggregationPair: Pair<String, IsAggregationRequest<*, *>>
    ) : this(aggregationPair.toMap())

    internal object Properties : ObjectPropertyDefinitions<Aggregations>() {
        val namedAggregations = add(
            1u, "namedAggregations",
            MapDefinition(
                keyDefinition = StringDefinition(),
                valueDefinition = MultiTypeDefinition(
                    typeEnum = AggregationRequestType
                )
            ),
            toSerializable = { value, _ ->
                value?.mapValues { (_, value) ->
                    TypedValue(value.aggregationType, value)
                }
            },
            fromSerializable = { values ->
                values?.mapValues { (_, value) ->
                    value.value
                }
            },
            getter = Aggregations::namedAggregations
        )
    }

    internal companion object : SingleValueDataModel<Map<String, TypedValue<AggregationRequestType, IsAggregationRequest<*, *>>>, Map<String, IsAggregationRequest<*, *>>, Aggregations, Properties, RequestContext>(
        properties = Properties,
        singlePropertyDefinition = Properties.namedAggregations
    ) {
        override fun invoke(values: ObjectValues<Aggregations, Properties>) = Aggregations(
            namedAggregations = values(1u)
        )
    }
}
