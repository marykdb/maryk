package maryk.core.aggregations.bucket

import maryk.core.aggregations.IsAggregator
import maryk.lib.time.IsTemporal

/** The aggregator to bucket dates */
data class DateHistogramAggregator<T: IsTemporal<*>>(
    override val request: DateHistogram<T>
) : IsAggregator<T, DateHistogram<T>, DateHistogramResponse<T>> {
    private var bucketAggregates = mutableListOf<BucketAggregator<T>>()

    override fun aggregate(value: T) {
        val roundedValue = value.roundToDateUnit(request.dateUnit)

        @Suppress("UNCHECKED_CAST")
        val index = bucketAggregates.binarySearch { (it.key as Comparable<Any>).compareTo(roundedValue) }
        val bucket = if (index < 0) {
            BucketAggregator(roundedValue).also {
                bucketAggregates.add(index * - 1 - 1, it)
            }
        } else {
            bucketAggregates[index]
        }

        bucket.aggregate()
    }

    override fun toResponse() =
        DateHistogramResponse(
            request.reference,
            bucketAggregates.map { value ->
                value.toResponse()
            }
        )
}
