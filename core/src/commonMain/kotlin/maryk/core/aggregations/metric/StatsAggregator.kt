package maryk.core.aggregations.metric

import maryk.core.aggregations.IsAggregator
import maryk.core.aggregations.ValueByPropertyReference

/** The aggregator to find stats for given reference */
data class StatsAggregator<T: Comparable<T>>(
    override val request: Stats<T>
) : IsAggregator<T, Stats<T>, StatsResponse<T>> {
    private val arithmeticDefinition = getArithmeticDefinition(
        request.reference.comparablePropertyDefinition,
        "Stats",
    )

    private var summedValue: T? = null
    private var valueCount: Long = 0L
    private var minValue: T? = null
    private var maxValue: T? = null

    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        @Suppress("UNCHECKED_CAST")
        val value = valueFetcher(request.reference) as T?

        if (value != null) {
            this.summedValue = summedValue?.let {
                arithmeticDefinition.add(it, value)
            } ?: value

            this.maxValue = this.maxValue?.let {
                maxOf(value, maxValue!!)
            } ?: value

            this.minValue = this.minValue?.let {
                minOf(value, minValue!!)
            } ?: value

            this.valueCount++
        }
    }

    override fun toResponse() =
        StatsResponse(
            request.reference,
            sum = summedValue,
            average = summedValue?.let {
                arithmeticDefinition.average(it, valueCount)
            },
            valueCount = valueCount.toULong(),
            min = minValue,
            max = maxValue
        )
}
