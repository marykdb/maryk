package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.IsAggregator

/** Bucket to be used while aggregating */
data class BucketAggregator<out T: Any>(
    val key: T
) {
    val aggregations: MutableMap<String, IsAggregator<*, *, *>> = mutableMapOf()
    var count: ULong = 0uL

    fun toResponse() = Bucket(
        key,
        AggregationsResponse(
            aggregations.map { (key, value) ->
                Pair(key, value.toResponse())
            }.toMap()
        ),
        count
    )

    fun aggregate() {
        count++
    }
}
