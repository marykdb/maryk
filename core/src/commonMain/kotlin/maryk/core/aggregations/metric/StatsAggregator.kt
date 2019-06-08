package maryk.core.aggregations.metric

import maryk.core.aggregations.IsAggregator
import maryk.core.properties.definitions.NumberDefinition

/** The aggregator to find stats for given reference */
data class StatsAggregator<T: Comparable<T>>(
    override val request: Stats<T>
) : IsAggregator<T, Stats<T>, StatsResponse<T>> {
    @Suppress("UNCHECKED_CAST")
    private val numberDefinition = request.reference.comparablePropertyDefinition as NumberDefinition<T>

    private var summedValue: T? = null
    private var valueCount: Long = 0L
    private var minValue: T? = null
    private var maxValue: T? = null

    override fun aggregate(value: T) {
        this.summedValue = summedValue?.let {
            numberDefinition.type.sum(it, value)
        } ?: value

        this.maxValue = this.maxValue?.let {
            maxOf(value, maxValue!!)
        } ?: value

        this.minValue = this.minValue?.let {
            minOf(value, minValue!!)
        } ?: value

        this.valueCount++
    }

    override fun toResponse() =
        StatsResponse(
            request.reference,
            sum = summedValue,
            average = summedValue?.let {
                numberDefinition.type.divide(
                    it,
                    numberDefinition.type.ofLong(valueCount)
                )
            },
            valueCount = valueCount.toULong(),
            min = minValue,
            max = maxValue
        )
}
