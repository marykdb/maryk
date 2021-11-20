package maryk.core.aggregations.bucket

import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.Aggregator
import maryk.core.aggregations.ValueByPropertyReference

/** Bucket to be used while aggregating */
internal class BucketAggregator<out T: Any>(
    val key: T,
    val aggregations: Aggregations?
) {
    private val aggregationsAggregator = aggregations?.let(::Aggregator)
    var count: ULong = 0uL

    /** Aggregate values to a response */
    fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        count++
        aggregationsAggregator?.aggregate(valueFetcher)
    }

    fun toResponse() = Bucket(
        key,
        aggregationsAggregator?.toResponse() ?: AggregationsResponse(emptyMap()),
        count
    )
}
