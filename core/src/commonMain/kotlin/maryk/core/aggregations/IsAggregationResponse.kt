package maryk.core.aggregations

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.query.RequestContext

/** Defines an aggregation response with a type so it can be transported */
interface IsAggregationResponse {
    val aggregationType: AggregationResponseType

    companion object {
        fun <DM: Any> addAggregationsDefinition(definitions: ObjectPropertyDefinitions<*>, getter: (DM) -> AggregationsResponse?) {
            @Suppress("UNCHECKED_CAST")
            definitions.add(
                2u,
                "aggregations",
                ContextTransformerDefinition(
                    EmbeddedObjectDefinition(dataModel = { AggregationsResponse })
                ) { context: RequestContext? -> context?.let { RequestContext(context.definitionsContext, context.dataModel) } },
                getter as (Any) -> AggregationsResponse?,
                alternativeNames = setOf("aggs")
            )
        }
    }
}
