package maryk.core.aggregations.metric

import maryk.core.aggregations.IsAggregator

/** The aggregator to find maximum value */
data class MaxAggregator<T: Comparable<T>>(
    override val request: Max<T>
) : IsAggregator<T, Max<T>, MaxResponse<T>> {
    private var maxValue: T? = null

    override fun aggregate(value: T) {
        this.maxValue = this.maxValue?.let {
            maxOf(value, maxValue!!)
        } ?: value
    }

    override fun toResponse() =
        MaxResponse(
            request.reference,
            maxValue
        )
}
