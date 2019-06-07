package maryk.core.aggregations

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition

/** Defines an aggregation response with a type so it can be transported */
interface IsAggregationResponse {
    val aggregationType: AggregationResponseType

    companion object {
        fun <DM: Any> addAggregationsDefinition(definitions: ObjectPropertyDefinitions<*>, getter: (DM) -> AggregationsResponse?) {
            @Suppress("UNCHECKED_CAST")
            definitions.add(
                2u,
                "aggregations",
                EmbeddedObjectDefinition(dataModel = { AggregationsResponse }),
                getter as (Any) -> AggregationsResponse?,
                alternativeNames = setOf("aggs")
            )
        }
    }
}
