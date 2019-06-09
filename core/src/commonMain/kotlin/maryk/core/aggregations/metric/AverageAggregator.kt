package maryk.core.aggregations.metric

import maryk.core.aggregations.IsAggregator
import maryk.core.aggregations.ValueByPropertyReference
import maryk.core.properties.definitions.NumberDefinition

/** The aggregator to find average value */
data class AverageAggregator<T: Comparable<T>>(
    override val request: Average<T>
) : IsAggregator<T, Average<T>, AverageResponse<T>> {
    @Suppress("UNCHECKED_CAST")
    private val numberDefinition = request.reference.comparablePropertyDefinition as NumberDefinition<T>

    private var summedValue: T? = null
    private var valueCount: Long = 0L

    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        @Suppress("UNCHECKED_CAST")
        val value = valueFetcher(request.reference) as T?

        if (value != null) {
            this.summedValue = summedValue?.let {
                numberDefinition.type.sum(it, value)
            } ?: value
            this.valueCount++
        }
    }

    override fun toResponse() =
        AverageResponse(
            request.reference,
            summedValue?.let {
                numberDefinition.type.divide(
                    it,
                    numberDefinition.type.ofLong(valueCount)
                )
            },
            valueCount.toULong()
        )
}
