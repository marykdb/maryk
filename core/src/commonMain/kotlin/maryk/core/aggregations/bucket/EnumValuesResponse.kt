package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationResponseType.EnumValuesType
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** The [buckets] found for all enum values at [reference] */
data class EnumValuesResponse<T: IndexedEnumComparable<T>>(
    val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>,
    val buckets: List<Bucket<T>> = emptyList()
) : IsAggregationResponse {
    override val aggregationType = EnumValuesType

    companion object : SimpleQueryModel<EnumValuesResponse<*>>() {
        val of by addReference(EnumValuesResponse<*>::reference)
        val buckets by list(
            index = 2u,
            getter = EnumValuesResponse<*>::buckets,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { Bucket }
            ),
            default = emptyList()
        )

        override fun invoke(values: SimpleObjectValues<EnumValuesResponse<*>>) =
            EnumValuesResponse<IndexedEnumComparable<Any>>(
                reference = values(of.index),
                buckets = values(buckets.index)
            )
    }
}
