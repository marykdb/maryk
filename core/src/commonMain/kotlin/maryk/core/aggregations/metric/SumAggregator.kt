package maryk.core.aggregations.metric

import maryk.core.aggregations.IsAggregator
import maryk.core.aggregations.ValueByPropertyReference

/** The aggregator to find sum of all values */
data class SumAggregator<T: Comparable<T>>(
    override val request: Sum<T>
): IsAggregator<T, Sum<T>, SumResponse<T>> {
    private val arithmeticDefinition = getArithmeticDefinition(
        request.reference.comparablePropertyDefinition,
        "Sum",
    )

    private var summedValue: T? = null

    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        @Suppress("UNCHECKED_CAST")
        val value = valueFetcher(request.reference) as T?

        if (value != null) {
            this.summedValue = this.summedValue?.let {
                arithmeticDefinition.add(it, value)
            } ?: value
        }
    }

    override fun toResponse() =
        SumResponse(
            request.reference,
            summedValue
        )
}
