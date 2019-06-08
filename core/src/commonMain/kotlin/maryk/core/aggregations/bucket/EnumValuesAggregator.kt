package maryk.core.aggregations.bucket

import maryk.core.aggregations.IsAggregator
import maryk.core.properties.enum.IndexedEnumComparable

/** The aggregator to bucket enum values */
data class EnumValuesAggregator<T: IndexedEnumComparable<T>>(
    override val request: EnumValues<T>
) : IsAggregator<T, EnumValues<T>, EnumValuesResponse<T>> {
    private var bucketAggregates = mutableListOf<BucketAggregator<T>>()

    override fun aggregate(value: T) {
        val index = bucketAggregates.binarySearch { it.key.compareTo(value) }
        val bucket = if (index < 0) {
            BucketAggregator(value).also {
                bucketAggregates.add(index * - 1 - 1, it)
            }
        } else {
            bucketAggregates[index]
        }

        bucket.aggregate()
    }

    override fun toResponse() =
        EnumValuesResponse(
            request.reference,
            bucketAggregates.map { value ->
                value.toResponse()
            }
        )
}
