package maryk.core.aggregations.metric

import maryk.core.aggregations.IsAggregator

/** The aggregator to find value count */
data class ValueCountAggregator<T: Comparable<T>>(
    override val request: ValueCount<T>
): IsAggregator<T, ValueCount<T>, ValueCountResponse<T>> {
    private var valueCount: Long = 0L

    override fun aggregate(value: T) {
        this.valueCount++
    }

    override fun toResponse() =
        ValueCountResponse(
            request.reference,
            valueCount.toULong()
        )
}
