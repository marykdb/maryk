package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationResponseType.EnumValuesType
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/** The [buckets] found for all enum values at [reference] */
data class EnumValuesResponse<T: IndexedEnum>(
    val reference: IsPropertyReference<out T, *, *>,
    val buckets: List<Bucket<T>> = emptyList()
) : IsAggregationResponse {
    override val aggregationType = EnumValuesType

    companion object : SimpleQueryDataModel<EnumValuesResponse<*>>(
        properties = object : ObjectPropertyDefinitions<EnumValuesResponse<*>>() {
            init {
                DefinedByReference.addReference(this, EnumValuesResponse<*>::reference, name = "of")
                add(2u, "buckets",
                    ListDefinition(
                        default = emptyList(),
                        valueDefinition = EmbeddedObjectDefinition(
                            dataModel = { Bucket }
                        )
                    ),
                    EnumValuesResponse<*>::buckets
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<EnumValuesResponse<*>>) =
            EnumValuesResponse(
                reference = values(1u),
                buckets = values(2u)
            )
    }
}
