package maryk.core.aggregations.metric

import maryk.core.aggregations.IsAggregator
import maryk.core.aggregations.ValueByPropertyReference

/** The aggregator to find minimum value */
data class MinAggregator<T: Comparable<T>>(
    override val request: Min<T>
) : IsAggregator<T, Min<T>, MinResponse<T>> {
    private var minValue: T? = null

    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        @Suppress("UNCHECKED_CAST")
        val value = valueFetcher(request.reference) as T?

        if (value != null) {
            this.minValue = this.minValue?.let {
                minOf(value, minValue!!)
            } ?: value
        }
    }

    override fun toResponse() =
        MinResponse(
            request.reference,
            minValue
        )
}
