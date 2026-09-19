package maryk.core.aggregations.metric

import maryk.core.aggregations.IsAggregator
import maryk.core.aggregations.ValueByPropertyReference

/** The aggregator to find value count */
data class ValueCountAggregator<T: Comparable<T>>(
    override val request: ValueCount<T>
): IsAggregator<T, ValueCount<T>, ValueCountResponse<T>> {
    private var valueCount: Long = 0L

    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        @Suppress("UNCHECKED_CAST")
        val value = valueFetcher(request.reference) as T?

        if (value != null) {
            this.valueCount++
        }
    }

    override fun toResponse() =
        ValueCountResponse(
            request.reference,
            valueCount.toULong()
        )
}
