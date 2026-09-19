package maryk.core.aggregations.metric

import maryk.core.aggregations.IsAggregator
import maryk.core.aggregations.ValueByPropertyReference

/** The aggregator to find maximum value */
data class MaxAggregator<T: Comparable<T>>(
    override val request: Max<T>
) : IsAggregator<T, Max<T>, MaxResponse<T>> {
    private var maxValue: T? = null

    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        @Suppress("UNCHECKED_CAST")
        val value = valueFetcher(request.reference) as T?

        if (value != null) {
            this.maxValue = this.maxValue?.let {
                maxOf(value, maxValue!!)
            } ?: value
        }
    }

    override fun toResponse() =
        MaxResponse(
            request.reference,
            maxValue
        )
}
