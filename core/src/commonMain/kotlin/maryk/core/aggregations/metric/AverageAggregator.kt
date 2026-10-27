package maryk.core.aggregations.metric

import maryk.core.aggregations.IsAggregator
import maryk.core.aggregations.ValueByPropertyReference

/** The aggregator to find average value */
data class AverageAggregator<T: Comparable<T>>(
    override val request: Average<T>
) : IsAggregator<T, Average<T>, AverageResponse<T>> {
    private val arithmeticDefinition = getArithmeticDefinition(
        request.reference.comparablePropertyDefinition,
        "Average",
    )

    private val accumulator = ArithmeticAccumulator(arithmeticDefinition)

    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        @Suppress("UNCHECKED_CAST")
        val value = valueFetcher(request.reference) as T?

        if (value != null) {
            accumulator.add(value)
        }
    }

    override fun toResponse() =
        AverageResponse(
            request.reference,
            accumulator.average(),
            accumulator.count.toULong()
        )
}
