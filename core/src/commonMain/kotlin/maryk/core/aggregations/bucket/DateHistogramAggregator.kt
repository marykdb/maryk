package maryk.core.aggregations.bucket

import maryk.core.aggregations.IsAggregator
import maryk.core.aggregations.ValueByPropertyReference
import maryk.core.properties.types.roundToDateUnit

/** The aggregator to bucket dates */
data class DateHistogramAggregator<T: Comparable<*>>(
    override val request: DateHistogram<T>
) : IsAggregator<T, DateHistogram<T>, DateHistogramResponse<T>> {
    private var bucketAggregates = mutableListOf<BucketAggregator<T>>()

    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        @Suppress("UNCHECKED_CAST")
        val value = valueFetcher(request.reference) as T?

        if (value != null) {
            val roundedValue = value.roundToDateUnit(request.dateUnit)

            @Suppress("UNCHECKED_CAST")
            val index = bucketAggregates.binarySearch { (it.key as Comparable<Any>).compareTo(roundedValue) }
            val bucket = if (index < 0) {
                BucketAggregator(roundedValue, request.aggregations).also {
                    bucketAggregates.add(index * -1 - 1, it)
                }
            } else {
                bucketAggregates[index]
            }

            bucket.aggregate(valueFetcher)
        }
    }

    override fun toResponse() =
        DateHistogramResponse(
            request.reference,
            bucketAggregates.map { value ->
                value.toResponse()
            }
        )
}
