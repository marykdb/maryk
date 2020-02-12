package maryk.core.aggregations

/** Defines an aggregation response with a type so it can be transported */
interface IsAggregationResponse {
    val aggregationType: AggregationResponseType
}
