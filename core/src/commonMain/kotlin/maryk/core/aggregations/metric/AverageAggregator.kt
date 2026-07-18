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

    private var summedValue: T? = null
    private var valueCount: Long = 0L

    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        @Suppress("UNCHECKED_CAST")
        val value = valueFetcher(request.reference) as T?

        if (value != null) {
            this.summedValue = summedValue?.let {
                arithmeticDefinition.add(it, value)
            } ?: value
            this.valueCount++
        }
    }

    override fun toResponse() =
        AverageResponse(
            request.reference,
            summedValue?.let {
                arithmeticDefinition.average(it, valueCount)
            },
            valueCount.toULong()
        )
}
