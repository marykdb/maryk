package maryk.core.aggregations.bucket

import maryk.core.aggregations.IsAggregator
import maryk.core.aggregations.ValueByPropertyReference
import maryk.core.properties.enum.IndexedEnumComparable

/** The aggregator to bucket enum values */
data class EnumValuesAggregator<T: IndexedEnumComparable<T>>(
    override val request: EnumValues<T>
) : IsAggregator<T, EnumValues<T>, EnumValuesResponse<T>> {
    private var bucketAggregates = mutableListOf<BucketAggregator<T>>()

    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        @Suppress("UNCHECKED_CAST")
        val value = valueFetcher(request.reference) as T?

        if (value != null) {
            val index = bucketAggregates.binarySearch { it.key compareTo value }
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
        EnumValuesResponse(
            request.reference,
            bucketAggregates.map { value ->
                value.toResponse()
            }
        )
}
