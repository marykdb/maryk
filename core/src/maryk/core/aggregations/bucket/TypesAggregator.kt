package maryk.core.aggregations.bucket

import maryk.core.aggregations.IsAggregator
import maryk.core.aggregations.ValueByPropertyReference
import maryk.core.properties.enum.TypeEnum

/** The aggregator to bucket multi types */
data class TypesAggregator<T: TypeEnum<*>>(
    override val request: Types<T>
) : IsAggregator<T, Types<T>, TypesResponse<T>> {
    private var bucketAggregates = mutableListOf<BucketAggregator<T>>()

    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        @Suppress("UNCHECKED_CAST")
        val value = valueFetcher(request.reference) as T?

        if (value != null) {
            @Suppress("UNCHECKED_CAST")
            val index = bucketAggregates.binarySearch { it.key as Comparable<Any> compareTo value }
            val bucket = if (index < 0) {
                BucketAggregator(value, request.aggregations).also {
                    bucketAggregates.add(index * -1 - 1, it)
                }
            } else {
                bucketAggregates[index]
            }

            bucket.aggregate(valueFetcher)
        }
    }

    override fun toResponse() =
        TypesResponse(
            request.reference,
            bucketAggregates.map { value ->
                value.toResponse()
            }
        )
}
