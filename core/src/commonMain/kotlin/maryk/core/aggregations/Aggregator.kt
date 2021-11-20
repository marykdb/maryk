package maryk.core.aggregations

/** Handles aggregating of multiple aggregations */
open class Aggregator(
    val aggregations: Aggregations
) {
    private val aggregators: MutableMap<String, IsAggregator<*, *, *>> = mutableMapOf()

    /**
     * Aggregate values from valueFetcher
     */
    open fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        aggregations.namedAggregations.let { namedAggregations ->
            for ((key, request) in namedAggregations) {
                val aggregator = this.aggregators.getOrPut(key, request::createAggregator)

                aggregator.aggregate(valueFetcher)
            }
        }
    }

    /** Converts aggregators into an AggregationsResponse */
    fun toResponse(): AggregationsResponse = AggregationsResponse(
        aggregators.map { (key, value) ->
            Pair(key, value.toResponse())
        }.toMap()
    )
}
