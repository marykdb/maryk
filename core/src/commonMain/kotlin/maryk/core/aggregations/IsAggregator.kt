package maryk.core.aggregations

/** Describes a metric aggregation */
interface IsAggregator<T: Any, RQ: IsAggregationRequest<*, RS>, RS: IsAggregationResponse> {
    val request: RQ

    /** Aggregate a [value] */
    fun aggregate(value: T)

    /** Convert the aggregator to a response */
    fun toResponse(): RS
}
