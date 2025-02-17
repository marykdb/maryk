package maryk.core.aggregations

/** Describes a metric aggregation */
interface IsAggregator<T: Any, RQ: IsAggregationRequest<T, *, RS>, RS: IsAggregationResponse> {
    val request: RQ

    /** Aggregate by [valueFetcher] */
    fun aggregate(valueFetcher: ValueByPropertyReference<*>)

    /** Convert the aggregator to a response */
    fun toResponse(): RS
}
