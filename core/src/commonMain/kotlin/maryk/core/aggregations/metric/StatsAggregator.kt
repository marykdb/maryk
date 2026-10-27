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

    private val accumulator = ArithmeticAccumulator(arithmeticDefinition)
    private var minValue: T? = null
    private var maxValue: T? = null

    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        @Suppress("UNCHECKED_CAST")
        val value = valueFetcher(request.reference) as T?

        if (value != null) {
            accumulator.add(value)

            maxValue = maxValue?.let {
                maxOf(value, it)
            } ?: value

            minValue = minValue?.let {
                minOf(value, it)
            } ?: value
        }
    }

    override fun toResponse() =
        StatsResponse(
            request.reference,
            sum = accumulator.sum(),
            average = accumulator.average(),
            valueCount = accumulator.count.toULong(),
            min = minValue,
            max = maxValue
        )
}
