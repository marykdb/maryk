package maryk.core.aggregations.metric

import maryk.core.aggregations.IsAggregator
import maryk.core.properties.definitions.NumberDefinition

/** The aggregator to find sum of all values */
data class SumAggregator<T: Comparable<T>>(
    override val request: Sum<T>
): IsAggregator<T, Sum<T>, SumResponse<T>> {
    @Suppress("UNCHECKED_CAST")
    private val numberDefinition = request.reference.comparablePropertyDefinition as NumberDefinition<T>

    private var summedValue: T? = null

    override fun aggregate(value: T) {
        this.summedValue = this.summedValue?.let {
            numberDefinition.type.sum(it, value)
        } ?: value
    }

    override fun toResponse() =
        SumResponse(
            request.reference,
            summedValue
        )
}
